package xyz.jasenon.lab.mqtt.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.dubbo.config.annotation.DubboService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskResultDto;
import xyz.jasenon.lab.mqtt.protocol.command.Task;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.event.GatewayClientReadyEvent;
import xyz.jasenon.lab.mqtt.client.event.GatewayClientsInitialRebuildCompletedEvent;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttCallback;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttClient;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.config.MqttOptions;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@DubboService
@Traced("mqtt-client-service")
public class SysClientManager implements MqttIo {

    private static final Logger log = LoggerFactory.getLogger(SysClientManager.class);
    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int INTERNAL_SERVER_ERROR = 500;
    private static final int MAX_MULTI_TARGETS = 20;

    private final TaskHelper thelper;
    private final GatewayHelper ghelper;
    private final ApplicationEventPublisher eventPublisher;
    private final Thread watchdog;
    private final MqttOptions options;
    private final VisibleLaboratoryScope visibleLaboratoryScope;

    public SysClientManager(
            TaskHelper thelper,
            GatewayHelper ghelper,
            ApplicationEventPublisher eventPublisher,
            MqttOptions options,
            VisibleLaboratoryScope visibleLaboratoryScope
    ) {
        this.thelper = thelper;
        this.ghelper = ghelper;
        this.eventPublisher = eventPublisher;
        this.options = options;
        this.visibleLaboratoryScope = visibleLaboratoryScope;
        watchdog = new Thread(this::watchdog);
        watchdog.setDaemon(true);
        watchdog.setName(this.getClass().getName() + "-" + "watchdog");
    }

    @PostConstruct
    public void startWatchdog() {
        watchdog.start();
    }

    @PreDestroy
    public void stopWatchdog() {
        watchdog.interrupt();
    }

    public RpcResult<MqttResponseDto> syncSend(MqttTaskDto dto) {
        MqttTask userTask = thelper.help(dto);
        if (userTask == null) throw new BusinessException(NOT_FOUND, "device doesn't exist!");
        assertVisible(userTask);
        try {
            return RpcResult.success(syncSendPrepared(userTask));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt request was interrupted");
        } catch (TimeoutException exception) {
            throw new BusinessException(504, "mqtt request timed out");
        } catch (ExecutionException exception) {
            Throwable cause = rootCause(exception);
            throw new BusinessException(INTERNAL_SERVER_ERROR,
                    cause.getMessage() == null ? "mqtt request failed" : cause.getMessage());
        }
    }

    private MqttResponseDto syncSendPrepared(MqttTask userTask)
            throws ExecutionException, InterruptedException, TimeoutException {
        var client = (AbstractSysClient<MqttTask>) ClientsRuntime.client(userTask.getGatewayId());
        if (client != null){
            PendingRequest<MqttTask> task = userTask.decorate();
            client.offer(task);
            return toResponseDto(task.getFuture().get(task.getTimeout(), TimeUnit.MILLISECONDS));
        }
        throw new BusinessException(NOT_FOUND, "gateway doesn't exist!");
    }

    public CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(MqttTaskDto dto) {
        return asyncSend(dto, true);
    }

    CompletableFuture<RpcResult<MqttResponseDto>> asyncSendFromRuleEngine(MqttTaskDto dto) {
        return asyncSend(dto, false);
    }

    private CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(
            MqttTaskDto dto,
            boolean requireUserVisibility
    ) {
        MqttTask userTask = thelper.help(dto);
        if (userTask == null) throw new BusinessException(NOT_FOUND, "device doesn't exist!");
        if (requireUserVisibility) {
            assertVisible(userTask);
        }
        var client = (AbstractSysClient<MqttTask>) ClientsRuntime.client(userTask.getGatewayId());
        if (client != null){
            PendingRequest<MqttTask> task = userTask.decorate();
            client.offer(task);
            return task.getFuture().thenApply(response -> RpcResult.success(toResponseDto(response)));
        }
        throw new BusinessException(NOT_FOUND, "gateway doesn't exist!");
    }

    @Override
    public RpcResult<List<MqttTaskResultDto>> multiSend(MqttMultiTaskDto task) {
        if (task == null || task.deviceIds() == null || task.deviceIds().isEmpty()) {
            throw new BusinessException(BAD_REQUEST, "至少需要选择一台设备");
        }
        List<String> deviceIds = task.deviceIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        if (deviceIds.isEmpty()) {
            throw new BusinessException(BAD_REQUEST, "至少需要选择一台设备");
        }
        if (deviceIds.size() > MAX_MULTI_TARGETS) {
            throw new BusinessException(BAD_REQUEST, "单次批量控制最多支持 20 台设备");
        }
        List<MqttTask> preparedTasks = deviceIds.stream().map(deviceId -> {
            MqttTaskDto single = MqttTaskDto.of(
                    task.commandLine(),
                    task.args() == null ? new int[0] : task.args().clone(),
                    task.type(),
                    deviceId
            );
            MqttTask prepared = thelper.help(single);
            if (prepared == null) {
                throw new BusinessException(NOT_FOUND, "设备不存在: " + deviceId);
            }
            assertVisible(prepared);
            return prepared;
        }).toList();

        return RpcResult.success(preparedTasks.stream().map(prepared -> {
            try {
                return MqttTaskResultDto.success(prepared.getDeviceId(), syncSendPrepared(prepared));
            } catch (Exception error) {
                return MqttTaskResultDto.failure(prepared.getDeviceId(), rootCause(error));
            }
        }).toList());
    }

    private void assertVisible(MqttTask task) {
        if (task.getLaboratoryId() == null
                || visibleLaboratoryScope.resolve(List.of(task.getLaboratoryId())).isEmpty()) {
            throw new BusinessException(FORBIDDEN, "无权控制该实验室设备");
        }
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private MqttResponseDto toResponseDto(Object resp) {
        if (resp instanceof Task task) {
            return MqttResponseDto.of(task.getGatewayId(), task.getPayload());
        }
        throw new BusinessException(INTERNAL_SERVER_ERROR, "unsupported mqtt response type");
    }

    public static void remove(AbstractSysClient<? extends Task> client) {
        ClientsRuntime.remove(client);
    }

    public static void register(AbstractSysClient<? extends Task> client)  {
        ClientsRuntime.register(client);
    }

    static AbstractSysClient<? extends Task> client(String gatewayId) {
        return ClientsRuntime.client(gatewayId);
    }

    static Set<String> clientIds() {
        return ClientsRuntime.clientIds();
    }

    /**
     * CRUD 写路径主动刷新运行时；周期看门狗只负责连接异常后的最终修复。
     */
    void registerGateway(RS485Gateway gateway) {
        synchronized (ClientsRuntime.class) {
            AbstractSysClient<? extends Task> previous = ClientsRuntime.remove(gateway.getId());
            close(previous);
            start(gateway, "GatewayLifecycle");
        }
    }

    void unregisterGateway(String gatewayId) {
        synchronized (ClientsRuntime.class) {
            close(ClientsRuntime.remove(gatewayId));
        }
    }

    /**
     * 借助GatewayHelper 提供的能力list all gatewayId
     * 遍历clients entryset 检查缺失了哪个 gateway
     * 由watchdog 重新拉起他  并使用slf4j 记录warn
     */
    private void watchdog(){
        initialRebuild();

        while(!Thread.currentThread().isInterrupted()){
            sleep(options.getGateway().getWatchdogIntervalMillis());

            try {
                rebuildClients();
            } catch (Exception e) {
                log.warn("watchdog check failed", e);
            }
        }
    }

    private void initialRebuild() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                rebuildClients();
                publishInitialRebuildCompleted();
                return;
            } catch (Exception e) {
                log.warn("initial gateway client rebuild failed", e);
            }
            sleep(options.getGateway().getWatchdogIntervalMillis());
        }
    }

    private void rebuildClients() {
        synchronized (ClientsRuntime.class) {
            List<RS485Gateway> gateways = ghelper.listAll();
            Set<String> clientIds = gateways.stream()
                    .map(RS485Gateway::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> nowClientIds = ClientsRuntime.clientIds();
            boolean equal = nowClientIds.equals(clientIds);
            // 补充缺失的  终止多余的
            if (!equal){
                for (RS485Gateway gateway : gateways) {
                    if (gateway.getId() == null || ClientsRuntime.contains(gateway.getId())) {
                        continue;
                    }

                    log.warn("[GatewayWatchDog] gateway-id:{} client missing, watchdog restarting", gateway.getId());
                    start(gateway, "GatewayWatchDog");
                }

                nowClientIds.stream()
                        .filter(clientId -> !clientIds.contains(clientId))
                        .forEach(clientId -> {
                            AbstractSysClient<? extends Task> client = ClientsRuntime.remove(clientId);
                            log.warn("gateway-id:{} client redundant, watchdog stopping", clientId);
                            close(client);
                        });
            }
        }
    }

    private void publishInitialRebuildCompleted() {
        log.info("[GatewayWatchDog] initial gateway client rebuild completed");
        eventPublisher.publishEvent(new GatewayClientsInitialRebuildCompletedEvent(clientIds()));
    }

    private void start(RS485Gateway gateway, String trigger) {
        if (gateway.getSendTopic() == null || gateway.getAcceptTopic() == null) {
            log.warn("[{}] gateway-id:{} topic missing, skip client start", trigger, gateway.getId());
            return;
        }
        if (options.getConnect().getUrl() == null || options.getConnect().getUrl().isBlank()) {
            log.warn("[{}] gateway-id:{} mqtt url missing, skip client start", trigger, gateway.getId());
            return;
        }

        MqttClient client = null;
        try {
            client = new MqttClient(
                    options.getConnect().getUrl(),
                    gateway.getId(),
                    gateway.getId(),
                    gateway.getSendTopic(),
                    gateway.getAcceptTopic()
            );
            client.setCallback(new MqttCallback(client));
            client.connect(connectOptions());
            register(client);
            if (ClientsRuntime.client(gateway.getId()) == client) {
                log.info("[{}] gateway-id:{} client registered", trigger, gateway.getId());
                eventPublisher.publishEvent(new GatewayClientReadyEvent(gateway.getId()));
            } else {
                close(client);
            }
        } catch (MqttException e) {
            log.warn("[{}] gateway-id:{} client start failed; watchdog will retry", trigger, gateway.getId(), e);
            ClientsRuntime.remove(client);
            close(client);
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(false);
        connectOptions.setConnectionTimeout(10);
        if (options.getConnect().getUsername() != null && !options.getConnect().getUsername().isBlank()) {
            connectOptions.setUserName(options.getConnect().getUsername());
        }
        if (options.getConnect().getPassword() != null && !options.getConnect().getPassword().isBlank()) {
            connectOptions.setPassword(options.getConnect().getPassword().toCharArray());
        }
        return connectOptions;
    }

    private void close(AbstractSysClient<? extends Task> client) {
        if (client == null) {
            return;
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (MqttException e) {
            log.warn("[GatewayWatchDog] gateway-id:{} disconnect failed", client.gatewayId, e);
        }

        try {
            client.close();
        } catch (MqttException e) {
            log.warn("[GatewayWatchDog] gateway-id:{} close failed", client.gatewayId, e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
