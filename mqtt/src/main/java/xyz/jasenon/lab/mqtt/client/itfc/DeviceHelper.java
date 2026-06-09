package xyz.jasenon.lab.mqtt.client.itfc;

import xyz.jasenon.lab.common.model.device.Device;

import java.util.List;

public interface DeviceHelper {

    List<Device> listAll();

    boolean updateDevice(Device device);

}
