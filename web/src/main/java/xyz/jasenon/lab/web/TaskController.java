package xyz.jasenon.lab.web;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/mqtt/tasks")
@Traced("mqtt-task-web")
public class TaskController {

    @DubboReference(check = false)
    private MqttIo mqttIo;

    @PostMapping
    @Traced(value = "mqtt.task.sync-send", recordResult = true)
    public DiyResponseEntity<R<MqttResponseDto>> syncSend(@RequestBody MqttTaskDto dto)
            throws ExecutionException, InterruptedException, TimeoutException {
        return DiyResponseEntity.of(R.success(mqttIo.syncSend(dto)));
    }
}
