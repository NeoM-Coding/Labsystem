package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.common.rpc.RpcResult;

/**
 * 管理 Mqtt module 中 Poll Task的启停
 */
public interface MqttPollCo {

    RpcResult<Pair<Boolean,String>> enable(String deviceId);

    RpcResult<Pair<Boolean,String>> disable(String deviceId);

}
