package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.common.error.ErrorCode;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper;

import java.util.List;

@Service
@AllArgsConstructor
public class MqttDeviceHelper implements DeviceHelper {

    private final DeviceMapper deviceMapper;

    @Override
    public Device getDeviceById(String deviceId) {
        return deviceMapper.getDeviceById(deviceId);
    }

    @Override
    public List<Device> listAll() {
        return deviceMapper.listAll();
    }

    @Override
    public List<Device> list(String gatewayId, String laboratoryId) {
        return deviceMapper.list(gatewayId, laboratoryId);
    }

    @Override
    public List<Device> listByLaboratories(String gatewayId, List<String> laboratoryIds) {
        return deviceMapper.listByLaboratories(gatewayId, laboratoryIds);
    }

    @Override
    public boolean addDevice(Device device) {
        lockLaboratory(device.getBelongTo());
        return deviceMapper.addDevice(device);
    }

    @Override
    public boolean updateDevice(Device device) {
        lockLaboratory(device.getBelongTo());
        return deviceMapper.updateDevice(device);
    }

    private void lockLaboratory(String laboratoryId) {
        if (laboratoryId != null && !laboratoryId.equals(deviceMapper.lockActiveLaboratory(laboratoryId))) {
            throw new BusinessException(ErrorCode.LABORATORY_NOT_FOUND);
        }
    }

    @Override
    public boolean updatePolling(String deviceId, boolean polling) {
        return deviceMapper.updatePolling(deviceId, polling);
    }

    @Override
    public boolean removeDevice(String deviceId) {
        return deviceMapper.removeDevice(deviceId);
    }
}
