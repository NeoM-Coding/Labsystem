package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;

import java.util.List;

/**
 * MQTT 网关管理契约。当前只开放 RS485/MQTT 网关。
 */
public interface MqttGatewayCRUD {

    RS485Gateway create(RS485Gateway gateway);

    RS485Gateway get(String gatewayId);

    List<RS485Gateway> list();

    RS485Gateway update(String gatewayId, RS485Gateway gateway);

    void delete(String gatewayId);
}
