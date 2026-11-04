package xyz.jasenon.lab.web.audit;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;

@Component
public class MqttMultiTaskAuditHandler extends AuditLogHandler<MqttMultiTaskDto> {

    public MqttMultiTaskAuditHandler() {
        super(MqttMultiTaskDto.class);
    }

    @Override
    protected AuditAction action(MqttMultiTaskDto event) {
        return AuditAction.EDIT;
    }

    @Override
    protected String objectType(MqttMultiTaskDto event) {
        return "device";
    }

    @Override
    protected String objectId(MqttMultiTaskDto event) {
        return event.deviceIds() == null ? null : event.deviceIds().toString();
    }
}
