package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

/**
 * MQTT 网关管理契约。当前只开放 RS485/MQTT 网关。
 */
public interface MqttGatewayCRUD {

    RpcResult<RS485Gateway> create(RS485Gateway gateway);

    RpcResult<RS485Gateway> get(String gatewayId);

    RpcResult<List<RS485Gateway>> list();

    RpcResult<List<RS485Gateway>> listByLaboratories(List<String> laboratoryIds);

    RpcResult<RS485Gateway> update(String gatewayId, RS485Gateway gateway);

    RpcResult<Void> delete(String gatewayId);
}
