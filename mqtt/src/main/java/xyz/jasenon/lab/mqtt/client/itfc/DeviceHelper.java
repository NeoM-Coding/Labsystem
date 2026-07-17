package xyz.jasenon.lab.mqtt.client.itfc;

import xyz.jasenon.lab.device.model.Device;

import java.util.List;

public interface DeviceHelper {

    Device getDeviceById(String deviceId);

    List<Device> listAll();

    boolean updateDevice(Device device);

}
