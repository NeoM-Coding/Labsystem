package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskResultDto;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface MqttIo {

    RpcResult<MqttResponseDto> syncSend(MqttTaskDto task);

    CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(MqttTaskDto task);

    RpcResult<List<MqttTaskResultDto>> multiSend(MqttMultiTaskDto task);
}
