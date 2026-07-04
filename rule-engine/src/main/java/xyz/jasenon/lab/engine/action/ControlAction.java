package xyz.jasenon.lab.engine.action;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;

/**
 * Sends one device-control task through MqttIo.asyncSend.
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
