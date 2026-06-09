package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttCallback;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttClient;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.config.MqttOptions;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@DubboService
public class SysClientManager implements MqttIo, MqttGatewayCRUD {

    private static final Logger log = LoggerFactory.getLogger(SysClientManager.class);
    private static final long WATCHDOG_INTERVAL_MILLIS = 6_0000L;

    private final static Map<String, AbstractSysClient<? extends Task>> clients = new ConcurrentHashMap<>();
    private final TaskHelper thelper;
    private final GatewayHelper ghelper;
    private final Thread watchdog;
    private final MqttOptions options;

    public SysClientManager(TaskHelper thelper, GatewayHelper ghelper, MqttOptions options) {
        this.thelper = thelper;
        this.ghelper = ghelper;
        this.options = options;
        watchdog = new Thread(this::watchdog);
        watchdog.setDaemon(true);
        watchdog.setName(this.getClass().getName() + "-" + "watchdog");
        watchdog.start();
    }

    public MqttResponseDto syncSend(MqttTaskDto dto) throws ExecutionException, InterruptedException, TimeoutException {
        MqttTask userTask = thelper.help(dto);
        if (userTask == null) throw new BusinessException(HttpStatus.NOT_FOUND.value(),"device doesn't exist!");
        var client = (AbstractSysClient<MqttTask>) clients.get(userTask.getGatewayId());
        if (client != null){
            PendingRequest<MqttTask> task = userTask.decorat();
            client.offer(task);
            return toResponseDto(task.getFuture().get(task.getTimeout(), TimeUnit.MILLISECONDS));
        }
        throw new BusinessException(HttpStatus.NOT_FOUND.value(),"gateway doesn't exist!");
    }

    public CompletableFuture<MqttResponseDto> asyncSend(MqttTaskDto dto) {
        MqttTask userTask = thelper.help(dto);
        if (userTask == null) throw new BusinessException(HttpStatus.NOT_FOUND.value(),"device doesn't exist!");
        var client = (AbstractSysClient<MqttTask>) clients.get(userTask.getGatewayId());
        if (client != null){
            PendingRequest<MqttTask> task = userTask.decorat();
            client.offer(task);
            return task.getFuture().thenApply(this::toResponseDto);
        }
        throw new BusinessException(HttpStatus.NOT_FOUND.value(),"gateway doesn't exist!");
    }

    private MqttResponseDto toResponseDto(Object resp) {
        if (resp instanceof Task task) {
            return MqttResponseDto.of(task.getGatewayId(), task.getPayload());
        }
        throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR.value(), "unsupported mqtt response type");
    }

    public static void remove(AbstractSysClient<? extends Task> client) {
        if (client == null) {
            return;
        }
        clients.remove(client.gatewayId, client);
    }

    public static void register(AbstractSysClient<? extends Task> client)  {
        if (client == null) {
            return;
        }
        clients.put(client.gatewayId,client);
    }

    /**
     * 借助GatewayHelper 提供的能力list all gatewayId
     * 遍历clients entryset 检查缺失了哪个 gateway
     * 由watchdog 重新拉起他  并使用slf4j 记录warn
     */
    private void watchdog(){
        while(!Thread.currentThread().isInterrupted()){
            try {
                synchronized (clients) {
                    List<RS485Gateway> gateways = ghelper.listAll();
                    Set<String> clientIds = gateways.stream()
                            .map(RS485Gateway::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    Set<String> nowClientIds = new HashSet<>(clients.keySet());
                    boolean equal = nowClientIds.equals(clientIds);
                    // 补充缺失的  终止多余的
                    if (!equal){
                        for (RS485Gateway gateway : gateways) {
                            if (gateway.getId() == null || clients.containsKey(gateway.getId())) {
                                continue;
                            }

                            log.warn("[GatewayWatchDog] gateway-id:{} client missing, watchdog restarting", gateway.getId());
                            start(gateway);
                        }

                        nowClientIds.stream()
                                .filter(clientId -> !clientIds.contains(clientId))
                                .forEach(clientId -> {
                                    AbstractSysClient<? extends Task> client = clients.remove(clientId);
                                    log.warn("gateway-id:{} client redundant, watchdog stopping", clientId);
                                    close(client);
                                });
                    }
                }
            } catch (Exception e) {
                log.warn("watchdog check failed", e);
            }

            sleep(WATCHDOG_INTERVAL_MILLIS);
        }
    }

    private void start(RS485Gateway gateway) {
        if (gateway.getSendTopic() == null || gateway.getAcceptTopic() == null) {
            log.warn("[GatewayWatchDog] gateway-id:{} topic missing, skip client restart", gateway.getId());
            return;
        }
        if (options.getUrl() == null || options.getUrl().isBlank()) {
            log.warn("[GatewayWatchDog] gateway-id:{} mqtt url missing, skip client restart", gateway.getId());
            return;
        }

        MqttClient client = null;
        try {
            client = new MqttClient(
                    options.getUrl(),
                    gateway.getId(),
                    gateway.getId(),
                    gateway.getSendTopic(),
                    gateway.getAcceptTopic()
            );
            client.setCallback(new MqttCallback(client));
            register(client);
            client.connect(connectOptions());
            if (clients.get(gateway.getId()) == client) {
                log.info("[GatewayWatchDog] gateway-id:{} client restarted by watchdog", gateway.getId());
            } else {
                close(client);
            }
        } catch (MqttException e) {
            log.warn("[GatewayWatchDog] gateway-id:{} client restart failed", gateway.getId(), e);
            clients.remove(gateway.getId(), client);
            close(client);
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);
        connectOptions.setAutomaticReconnect(false);
        connectOptions.setConnectionTimeout(10);
        if (options.getUsername() != null && !options.getUsername().isBlank()) {
            connectOptions.setUserName(options.getUsername());
        }
        if (options.getPassword() != null && !options.getPassword().isBlank()) {
            connectOptions.setPassword(options.getPassword().toCharArray());
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
