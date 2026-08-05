package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

/**
 * MQTT 设备管理契约。设备模型通过 deviceType 保留具体子类型信息。
 */
public interface MqttDeviceCRUD {

    RpcResult<Device> create(Device device);

    RpcResult<Device> get(String deviceId);

    RpcResult<List<Device>> list(String gatewayId, String laboratoryId);

    RpcResult<List<Device>> listByLaboratories(String gatewayId, List<String> laboratoryIds);

    RpcResult<Device> update(String deviceId, Device device);

    RpcResult<Void> delete(String deviceId);
}
