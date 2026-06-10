package xyz.jasenon.lab.mqtt.client;

import jakarta.annotation.PreDestroy;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.event.EventListener;
import xyz.jasenon.lab.api.mqtt.MqttPollCo;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.mqtt.client.common.Poll;
import xyz.jasenon.lab.mqtt.client.event.GatewayClientReadyEvent;
import xyz.jasenon.lab.mqtt.client.event.GatewayClientsInitialRebuildCompletedEvent;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.config.MqttOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 这个class 的功能就是管理所有device的轮询情况
 * 通过distinct gateway_id 来分组device 然后通过
 * gateway_id 在 {@link SysClientManager} 中拿到
 * 对应的client 检查 {@link xyz.jasenon.lab.common.SetQueue} 中是否有对应的
 * {@link xyz.jasenon.lab.mqtt.client.common.Poll}
 */
@DubboService
public class SysPollingManager implements MqttPollCo {

    private static final Logger log = LoggerFactory.getLogger(SysPollingManager.class);

    private final DeviceHelper dhelper;
    private final TaskHelper thelper;
    private final MqttOptions options;

    /**
     * watch dog 用于检查 对应client 中是否完全覆盖当前情况
     */
    private final Thread watchdog;
    private final Object watchdogMonitor = new Object();

    public SysPollingManager(DeviceHelper dhelper, TaskHelper thelper, MqttOptions options) {
        this.dhelper = dhelper;
        this.thelper = thelper;
        this.options = options;
        this.watchdog = new Thread(this::watchdog);
        this.watchdog.setDaemon(true);
        this.watchdog.setName(this.getClass().getName() + "-watchdog");
    }

    @PreDestroy
    public void stop() {
        watchdog.interrupt();
    }

    @Override
    public Pair<Boolean, String> enable(String deviceId) {
        Device device = dhelper.getDeviceById(deviceId);
        if (device == null) {
            return Pair.of(false, "device doesn't exist");
        }

        Poll<MqttTask> poll = pollOf(device);
        if (poll == null) {
            return Pair.of(false, "poll task can't be created");
        }

        device.setPolling(true);
        if (!dhelper.updateDevice(device)) {
            return Pair.of(false, "device polling status update failed");
        }

        if (!SysClientManager.clientIds().contains(device.getGatewayId())) {
            return Pair.of(true, "poll target enabled, but gateway client isn't ready");
        }

        AbstractSysClient<MqttTask> client = pollClient(device.getGatewayId());
        boolean offered = client != null && client.offer(poll);
        if (offered) {
            return Pair.of(true, "poll enabled");
        }

        if (client == null) {
            return Pair.of(true, "poll target enabled, but gateway client isn't ready");
        }
        return Pair.of(true, "poll already enabled");
    }

    @Override
    public Pair<Boolean, String> disable(String deviceId) {
        Device device = dhelper.getDeviceById(deviceId);
        if (device == null) {
            return Pair.of(false, "device doesn't exist");
        }

        Poll<MqttTask> poll = pollOf(device);
        device.setPolling(false);
        if (!dhelper.updateDevice(device)) {
            return Pair.of(false, "device polling status update failed");
        }

        if (poll != null) {
            AbstractSysClient<MqttTask> client = pollClient(device.getGatewayId());
            if (client != null) {
                client.remove(poll);
            }
        }
        return Pair.of(true, "poll disabled");
    }

    @EventListener
    public void onGatewayClientReady(GatewayClientReadyEvent event) {
        syncRuntimeForGateways(Set.of(event.getGatewayId()), "GatewayClientReadyEvent");
    }

    @EventListener
    public void onGatewayClientsInitialRebuildCompleted(GatewayClientsInitialRebuildCompletedEvent event) {
        Set<String> gatewayIds = event.getGatewayIds().isEmpty() ? SysClientManager.clientIds() : event.getGatewayIds();
        syncRuntimeForGateways(gatewayIds, "GatewayClientsInitialRebuildCompletedEvent");
        startWatchdog();
    }

    private void watchdog() {
        while (!Thread.currentThread().isInterrupted()) {
            syncRuntimeForGateways(SysClientManager.clientIds(), "PollingWatchDog");
            sleep(options.getPoll().getWatchdogIntervalMillis());
        }
    }

    private void startWatchdog() {
        synchronized (watchdogMonitor) {
            if (watchdog.getState() != Thread.State.NEW) {
                return;
            }
            watchdog.start();
            log.info("[PollingWatchDog] started after gateway initial rebuild event");
        }
    }

    private void syncRuntimeForGateways(Set<String> readyGatewayIds, String trigger) {
        if (readyGatewayIds == null || readyGatewayIds.isEmpty()) {
            log.warn("[{}] no ready gateway client found, skip poll sync", trigger);
            return;
        }

        try {
            Map<String, Set<Poll<MqttTask>>> targetByGateway = dhelper.listAll().stream()
                    .filter(Objects::nonNull)
                    .filter(Device::isPolling)
                    .map(this::pollOf)
                    .filter(Objects::nonNull)
                    .filter(poll -> readyGatewayIds.contains(poll.poll().getRequest().getGatewayId()))
                    .collect(Collectors.groupingBy(
                            poll -> poll.poll().getRequest().getGatewayId(),
                            Collectors.toSet()
                    ));

            targetByGateway.forEach((gatewayId, targetPolls) -> {
                AbstractSysClient<MqttTask> client = pollClient(gatewayId);
                if (client == null) {
                    log.warn(
                            "[{}] gateway-id:{} disappeared from client manager, skip {} target polls",
                            trigger,
                            gatewayId,
                            targetPolls.size()
                    );
                    return;
                }

                Set<Poll<MqttTask>> activePolls = client.pollSnapshot();
                targetPolls.stream()
                        .filter(poll -> !activePolls.contains(poll))
                        .forEach(missingPoll -> {
                            boolean offered = client.offer(missingPoll);
                            MqttTask task = missingPoll.poll().getRequest();
                            if (offered) {
                                log.warn(
                                        "[{}] gateway-id:{} device-id:{} poll missing from active set, registered",
                                        trigger,
                                        task.getGatewayId(),
                                        task.getDeviceId()
                                );
                            } else {
                                log.warn(
                                        "[{}] gateway-id:{} device-id:{} poll missing check failed to offer",
                                        trigger,
                                        task.getGatewayId(),
                                        task.getDeviceId()
                                );
                            }
                        });
                    });
        } catch (Exception e) {
            log.warn("[{}] sync poll runtime state failed", trigger, e);
        }
    }

    private Poll<MqttTask> pollOf(Device device) {
        if (device == null || device.getGatewayId() == null || device.getGatewayId().isBlank()) {
            return null;
        }
        if (device.getDeviceType() != DeviceType.Access) {
            log.warn(
                    "[PollingWatchDog] device-id:{} type:{} has no polling command mapping, skip",
                    device.getId(),
                    device.getDeviceType()
            );
            return null;
        }

        MqttTaskDto dto = new MqttTaskDto();
        dto.setDeviceId(device.getId());
        dto.setType(device.getDeviceType());
        dto.setCommandLine(CommandLine.REQUEST_ACCESS_DATA);
        dto.setArgs(new int[0]);

        MqttTask task = thelper.help(dto);
        if (task == null) {
            return null;
        }
        return new Poll<>(task, options.getPoll().getTimeoutMillis(), options.getPoll().getIntervalMillis());
    }

    @SuppressWarnings("unchecked")
    private AbstractSysClient<MqttTask> pollClient(String gatewayId) {
        AbstractSysClient<?> client = SysClientManager.client(gatewayId);
        if (client == null) {
            return null;
        }
        return (AbstractSysClient<MqttTask>) client;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
