package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.MqttRuleIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@DubboService(group = MqttRuleIo.DUBBO_GROUP)
@Traced("rule-mqtt-client-service")
public class RuleMqttIoService implements MqttRuleIo {

    private final SysClientManager clientManager;

    public RuleMqttIoService(SysClientManager clientManager) {
        this.clientManager = Objects.requireNonNull(clientManager, "clientManager");
    }

    @Override
    public CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(MqttTaskDto task) {
        return clientManager.asyncSendFromRuleEngine(task);
    }
}
