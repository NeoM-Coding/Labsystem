package xyz.jasenon.lab.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskResultDto;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/mqtt/tasks")
@Traced("mqtt-task-web")
@Tag(name = "MQTT任务", description = "向 MQTT 设备发送即时控制或查询任务")
public class TaskController {

    @DubboReference(check = false, timeout = 120000)
    private MqttIo mqttIo;

    @PostMapping
    @Operation(summary = "同步发送MQTT任务", description = "向指定 MQTT 设备发送任务，并等待设备响应或请求超时。")
    @Traced(value = "mqtt.task.sync-send", recordResult = true)
    public DiyResponseEntity<R<MqttResponseDto>> syncSend(@RequestBody MqttTaskDto dto) {
        return DiyResponseEntity.of(R.success(RpcClient.call(() -> mqttIo.syncSend(dto))));
    }

    @PostMapping("/multi")
    @Operation(summary = "批量发送MQTT任务", description = "向最多 20 台同类型 MQTT 设备串行发送相同指令，并返回逐设备执行结果。")
    @Traced(value = "mqtt.task.multi-send", recordResult = true)
    public DiyResponseEntity<R<List<MqttTaskResultDto>>> multiSend(@RequestBody MqttMultiTaskDto dto) {
        return DiyResponseEntity.of(R.success(RpcClient.call(() -> mqttIo.multiSend(dto))));
    }
}
