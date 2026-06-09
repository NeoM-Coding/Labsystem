package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.common.util.Pair;

/**
 * 管理 Mqtt module 中 Poll Task的启停
 */
public interface MqttPollCo {

    Pair<Boolean,String> enable(String deviceId);

    Pair<Boolean,String> disable(String deviceId);

}
