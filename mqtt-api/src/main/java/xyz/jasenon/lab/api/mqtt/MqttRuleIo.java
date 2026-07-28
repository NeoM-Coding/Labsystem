package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.concurrent.CompletableFuture;

/**
 * 规则引擎执行设备动作使用的内部 MQTT 契约。
 *
 * <p>规则动作由系统调度线程触发，不继承 Web 登录会话，因此不能按当前用户的
 * 实验室可见范围鉴权。该契约使用独立 Dubbo group，避免与面向用户的
 * {@link MqttIo} 混用。</p>
 */
public interface MqttRuleIo {

    String DUBBO_GROUP = "rule-engine-internal";

    CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(MqttTaskDto task);
}
