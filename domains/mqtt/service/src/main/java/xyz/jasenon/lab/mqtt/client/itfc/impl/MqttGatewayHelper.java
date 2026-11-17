package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.gateway.Gateway;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.GatewayMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

@AllArgsConstructor
@Service
public class MqttGatewayHelper implements GatewayHelper {

    private static final Pattern MYSQL_DUPLICATE_INDEX = Pattern.compile(" for key '([^']+)'$");

    private final GatewayMapper gatewayMapper;

    @Override
    public List<RS485Gateway> listAll() {
        return gatewayMapper.listAll();
    }

    @Override
    public RS485Gateway getById(String gatewayId) {
        return gatewayMapper.getById(gatewayId);
    }

    @Override
    public boolean addRS485Gateway(RS485Gateway gateway) {
        lockLaboratories(gateway);
        try {
            return gatewayMapper.addRS485Gateway(gateway);
        } catch (DuplicateKeyException exception) {
            throw translateDuplicateKey(exception);
        }
    }

    @Override
    public boolean updateRS485Gateway(RS485Gateway gateway) {
        lockLaboratories(gateway);
        try {
            return gatewayMapper.updateRS485Gateway(gateway);
        } catch (DuplicateKeyException exception) {
            throw translateDuplicateKey(exception);
        }
    }

    private void lockLaboratories(RS485Gateway gateway) {
        List<String> laboratoryIds = gateway.getUsingIn();
        if (laboratoryIds == null) {
            return;
        }
        if (laboratoryIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new BusinessException(ErrorCode.LABORATORY_ID_REQUIRED);
        }
        for (String id : laboratoryIds.stream().distinct().sorted().toList()) {
            if (!id.equals(gatewayMapper.lockActiveLaboratory(id))) {
                throw new BusinessException(ErrorCode.LABORATORY_NOT_FOUND);
            }
        }
    }

    private static RuntimeException translateDuplicateKey(DuplicateKeyException exception) {
        if (!(exception.getMostSpecificCause() instanceof SQLException sql) || sql.getMessage() == null) {
            return exception;
        }
        var match = MYSQL_DUPLICATE_INDEX.matcher(sql.getMessage());
        if (!match.find()) {
            return exception;
        }
        String key = match.group(1);
        ErrorCode code = switch (key.substring(key.lastIndexOf('.') + 1)) {
            case "uk_gateway_rs485_send_topic", "uk_gateway_rs485_active_send_topic" ->
                    ErrorCode.MQTT_GATEWAY_SEND_TOPIC_CONFLICT;
            case "uk_gateway_rs485_accept_topic", "uk_gateway_rs485_active_accept_topic" ->
                    ErrorCode.MQTT_GATEWAY_ACCEPT_TOPIC_CONFLICT;
            default -> null;
        };
        if (code == null) {
            return exception;
        }
        return BusinessException.causedBy(code, exception);
    }

    @Override
    public boolean removeRS485Gateway(String gatewayId) {
        return gatewayMapper.removeRS485Gateway(gatewayId);
    }

    @Override
    public List<Gateway> listRange(int workerId, int works) {
        throw new UnsupportedOperationException("not implements now!");
    }
}
