package xyz.jasenon.lab.mqtt.client.itfc;

import xyz.jasenon.lab.device.model.Device;

import java.util.List;

public interface DeviceHelper {

    Device getDeviceById(String deviceId);

    List<Device> listAll();

    List<Device> list(String gatewayId, String laboratoryId);

    boolean addDevice(Device device);

    boolean updateDevice(Device device);

    boolean removeDevice(String deviceId);

}
