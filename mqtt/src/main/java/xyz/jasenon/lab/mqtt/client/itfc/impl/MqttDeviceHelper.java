package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper;

import java.util.List;

@Service
@AllArgsConstructor
public class MqttDeviceHelper implements DeviceHelper {

    private final DeviceMapper deviceMapper;

    @Override
    public List<Device> listAll() {
        return deviceMapper.listAll();
    }

    @Override
    public boolean updateDevice(Device device) {
        return false;
    }
}
