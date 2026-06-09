package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.MqttPollCo;
import xyz.jasenon.lab.common.util.Pair;

/**
 * 这个class 的功能就是管理所有device的轮询情况
 * 通过distinct gateway_id 来分组device 然后通过
 * gateway_id 在 {@link SysClientManager} 中拿到
 * 对应的client 检查 {@link xyz.jasenon.lab.common.SetQueue} 中是否有对应的
 * {@link xyz.jasenon.lab.mqtt.client.common.Poll}
 */
@DubboService
public class SysPollingManager implements MqttPollCo {
    /**
     * watch dog 用于检查 对应client 中是否完全覆盖当前情况
     */
    private final Thread watchdog;

    @Override
    public Pair<Boolean, String> enable(String deviceId) {
        return null;
    }

    @Override
    public Pair<Boolean, String> disable(String deviceId) {
        return null;
    }
}
