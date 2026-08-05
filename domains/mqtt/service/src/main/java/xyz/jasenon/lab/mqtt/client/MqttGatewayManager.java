package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.device.model.gateway.GatewayType;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;
import java.util.Set;

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
    private final VisibleLaboratoryScope visibleLaboratoryScope;

    @Autowired
    public MqttGatewayManager(GatewayHelper gatewayHelper,
                              DeviceHelper deviceHelper,
                              SysClientManager clientManager,
                              VisibleLaboratoryScope visibleLaboratoryScope) {
        this.gatewayHelper = gatewayHelper;
        this.deviceHelper = deviceHelper;
        this.clientManager = clientManager;
        this.visibleLaboratoryScope = visibleLaboratoryScope;
    }

    MqttGatewayManager(GatewayHelper gatewayHelper,
                       DeviceHelper deviceHelper,
                       SysClientManager clientManager) {
        this(gatewayHelper, deviceHelper, clientManager, new VisibleLaboratoryScope());
    }

    @Override
    @Transactional
    public RpcResult<RS485Gateway> create(RS485Gateway gateway) {
        validate(gateway);
        gateway.setGatewayType(GatewayType.RS485);
        if (!gatewayHelper.addRS485Gateway(gateway)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway create failed");
        }
        RS485Gateway created = required(gateway.getId());
        TransactionCallbacks.afterCommit(() -> clientManager.registerGateway(created));
        return RpcResult.success(created);
    }

    @Override
    public RpcResult<RS485Gateway> get(String gatewayId) {
        return RpcResult.success(required(gatewayId));
    }

    @Override
    public RpcResult<List<RS485Gateway>> list() {
        return RpcResult.success(gatewayHelper.listAll());
    }

    @Override
    public RpcResult<List<RS485Gateway>> listByLaboratories(List<String> laboratoryIds) {
        Set<String> visibleIds = Set.copyOf(visibleLaboratoryScope.resolve(laboratoryIds));
        if (visibleIds.isEmpty()) {
            return RpcResult.success(List.of());
        }
        return RpcResult.success(gatewayHelper.listAll().stream()
                .filter(gateway -> gateway.getUsingIn() != null
                        && gateway.getUsingIn().stream().anyMatch(visibleIds::contains))
                .toList());
    }

    @Override
    @Transactional
    public RpcResult<RS485Gateway> update(String gatewayId, RS485Gateway gateway) {
        required(gatewayId);
        validate(gateway);
        gateway.setId(gatewayId);
        gateway.setGatewayType(GatewayType.RS485);
        if (!gatewayHelper.updateRS485Gateway(gateway)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway update failed");
        }
        RS485Gateway updated = required(gatewayId);
        // Topic 或连接身份可能变化，提交后立即替换旧 client。
        TransactionCallbacks.afterCommit(() -> clientManager.registerGateway(updated));
        return RpcResult.success(updated);
    }

    @Override
    @Transactional
    public RpcResult<Void> delete(String gatewayId) {
        required(gatewayId);
        if (!deviceHelper.list(gatewayId, null).isEmpty()) {
            throw new BusinessException(CONFLICT, "mqtt gateway still has devices");
        }
        if (!gatewayHelper.removeRS485Gateway(gatewayId)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "mqtt gateway delete failed");
        }
        TransactionCallbacks.afterCommit(() -> clientManager.unregisterGateway(gatewayId));
        return RpcResult.success();
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
