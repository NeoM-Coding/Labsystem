package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.gateway.GatewayType;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;

@DubboService
@Traced("mqtt-gateway-service")
public class MqttGatewayManager implements MqttGatewayCRUD {

    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final int INTERNAL_SERVER_ERROR = 500;

    private final GatewayHelper gatewayHelper;
    private final DeviceHelper deviceHelper;
    private final SysClientManager clientManager;

    public MqttGatewayManager(GatewayHelper gatewayHelper,
                              DeviceHelper deviceHelper,
                              SysClientManager clientManager) {
        this.gatewayHelper = gatewayHelper;
        this.deviceHelper = deviceHelper;
        this.clientManager = clientManager;
    }

    @Override
    public RS485Gateway create(RS485Gateway gateway) {
        validate(gateway);
        gateway.setGatewayType(GatewayType.RS485);
        if (!gatewayHelper.addRS485Gateway(gateway)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway create failed");
        }
        clientManager.registerGateway(gateway);
        return required(gateway.getId());
    }

    @Override
    public RS485Gateway get(String gatewayId) {
        return required(gatewayId);
    }

    @Override
    public List<RS485Gateway> list() {
        return gatewayHelper.listAll();
    }

    @Override
    public RS485Gateway update(String gatewayId, RS485Gateway gateway) {
        required(gatewayId);
        validate(gateway);
        gateway.setId(gatewayId);
        gateway.setGatewayType(GatewayType.RS485);
        if (!gatewayHelper.updateRS485Gateway(gateway)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway update failed");
        }
        // Topic 或连接身份可能变化，更新后必须替换旧 client，而不是等看门狗发现。
        clientManager.registerGateway(gateway);
        return required(gatewayId);
    }

    @Override
    public void delete(String gatewayId) {
        required(gatewayId);
        if (!deviceHelper.list(gatewayId, null).isEmpty()) {
            throw new BusinessException(CONFLICT, "mqtt gateway still has devices");
        }
        if (!gatewayHelper.removeRS485Gateway(gatewayId)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway delete failed");
        }
        clientManager.unregisterGateway(gatewayId);
    }

    private RS485Gateway required(String gatewayId) {
        if (gatewayId == null || gatewayId.isBlank()) {
            throw new BusinessException(BAD_REQUEST, "gateway id is required");
        }
        RS485Gateway gateway = gatewayHelper.getById(gatewayId);
        if (gateway == null) {
            throw new BusinessException(NOT_FOUND, "mqtt gateway doesn't exist");
        }
        return gateway;
    }

    private void validate(RS485Gateway gateway) {
        if (gateway == null) {
            throw new BusinessException(BAD_REQUEST, "mqtt gateway is required");
        }
        if (gateway.getSendTopic() == null || gateway.getSendTopic().isBlank()
                || gateway.getAcceptTopic() == null || gateway.getAcceptTopic().isBlank()) {
            throw new BusinessException(BAD_REQUEST, "mqtt gateway topics are required");
        }
    }
}
