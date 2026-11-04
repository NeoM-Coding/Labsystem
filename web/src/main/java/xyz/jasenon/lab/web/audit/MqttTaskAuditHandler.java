package xyz.jasenon.lab.web.audit;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;

@Component
public class MqttTaskAuditHandler extends AuditLogHandler<MqttTaskDto> {

    public MqttTaskAuditHandler() {
        super(MqttTaskDto.class);
    }

    @Override
    protected AuditAction action(MqttTaskDto event) {
        return AuditAction.EDIT;
    }

    @Override
    protected String objectType(MqttTaskDto event) {
        return "device";
    }

    @Override
    protected String objectId(MqttTaskDto event) {
        return event.getDeviceId();
    }
}
