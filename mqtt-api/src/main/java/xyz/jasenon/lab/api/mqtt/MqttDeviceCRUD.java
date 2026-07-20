package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.device.model.Device;

import java.util.List;

/**
 * MQTT 设备管理契约。设备模型通过 deviceType 保留具体子类型信息。
 */
public interface MqttDeviceCRUD {

    Device create(Device device);

    Device get(String deviceId);

    List<Device> list(String gatewayId, String laboratoryId);

    Device update(String deviceId, Device device);

    void delete(String deviceId);
}
