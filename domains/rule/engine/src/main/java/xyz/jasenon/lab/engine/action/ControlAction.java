package xyz.jasenon.lab.engine.action;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;

/**
 * 通过规则引擎内部 MQTT RPC 异步发送一次设备控制任务。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlAction implements Action{

    private String actionGroupId;
    private MqttTaskDto control;

    @Override
    public ActionType is() {
        return ActionType.Control;
    }
}
