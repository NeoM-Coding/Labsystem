package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.MqttDeviceCRUD;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.device.model.Address;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.device.model.SelfId;
import xyz.jasenon.lab.device.model.devices.Access;
import xyz.jasenon.lab.device.model.devices.AirCondition;
import xyz.jasenon.lab.device.model.devices.CircuitBreak;
import xyz.jasenon.lab.device.model.devices.Light;
import xyz.jasenon.lab.device.model.devices.Sensor;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;

@DubboService
@Traced("mqtt-device-service")
public class MqttDeviceManager implements MqttDeviceCRUD {

    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    private static final int INTERNAL_SERVER_ERROR = 500;

    private final DeviceHelper deviceHelper;
    private final GatewayHelper gatewayHelper;
    private final SysPollingManager pollingManager;

    public MqttDeviceManager(DeviceHelper deviceHelper,
                             GatewayHelper gatewayHelper,
                             SysPollingManager pollingManager) {
        this.deviceHelper = deviceHelper;
        this.gatewayHelper = gatewayHelper;
        this.pollingManager = pollingManager;
    }

    @Override
    public Device create(Device device) {
        validate(device);
        if (!deviceHelper.addDevice(device)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "device create failed");
        }
        pollingManager.synchronizeRuntime(null, device);
        return required(device.getId());
    }

    @Override
    public Device get(String deviceId) {
        return required(deviceId);
    }

    @Override
    public List<Device> list(String gatewayId, String laboratoryId) {
        return deviceHelper.list(gatewayId, laboratoryId);
    }

    @Override
    public Device update(String deviceId, Device device) {
        Device previous = required(deviceId);
        validate(device);
        if (previous.getDeviceType() != device.getDeviceType()) {
            throw new BusinessException(BAD_REQUEST, "device type can't be changed");
        }

        // URL/RPC 方法参数中的 ID 是资源身份，禁止请求体把更新导向其他设备。
        device.setId(deviceId);
        if (!deviceHelper.updateDevice(device)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "device update failed");
        }
        pollingManager.synchronizeRuntime(previous, device);
        return required(deviceId);
    }

    @Override
    public void delete(String deviceId) {
        Device previous = required(deviceId);
        if (!deviceHelper.removeDevice(deviceId)) {
            throw new BusinessException(INTERNAL_SERVER_ERROR, "device delete failed");
        }
        pollingManager.synchronizeRuntime(previous, null);
    }

    private Device required(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException(BAD_REQUEST, "device id is required");
        }
        Device device = deviceHelper.getDeviceById(deviceId);
        if (device == null) {
            throw new BusinessException(NOT_FOUND, "device doesn't exist");
        }
        return device;
    }

    private void validate(Device device) {
        if (device == null || device.getDeviceType() == null) {
            throw new BusinessException(BAD_REQUEST, "device type is required");
        }
        if (device.getGatewayId() == null || device.getGatewayId().isBlank()) {
            throw new BusinessException(BAD_REQUEST, "gateway id is required");
        }
        if (gatewayHelper.getById(device.getGatewayId()) == null) {
            throw new BusinessException(BAD_REQUEST, "mqtt gateway doesn't exist");
        }
        if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
            throw new BusinessException(BAD_REQUEST, "device name is required");
        }
        if (!matchesConcreteType(device)) {
            throw new BusinessException(BAD_REQUEST, "device type doesn't match its model");
        }
        validateAddress(device);
    }

    private boolean matchesConcreteType(Device device) {
        return switch (device.getDeviceType()) {
            case Access -> device instanceof Access;
            case AirCondition -> device instanceof AirCondition;
            case CircuitBreak -> device instanceof CircuitBreak;
            case Light -> device instanceof Light;
            case Sensor -> device instanceof Sensor;
        };
    }

    private void validateAddress(Device device) {
        int address = ((Address) device).address();
        DeviceType type = device.getDeviceType();
        boolean valid = switch (type) {
            case Access -> address >= 1 && address <= 10;
            case CircuitBreak -> address >= 11 && address <= 30;
            case AirCondition -> address >= 31 && address <= 40;
            case Light -> address >= 41 && address <= 60;
            case Sensor -> address >= 61 && address <= 80;
        };
        if (!valid) {
            throw new BusinessException(BAD_REQUEST, "device address is outside the allowed range");
        }
        if (device instanceof SelfId selfId && selfId.selfId() < 0) {
            throw new BusinessException(BAD_REQUEST, "device self id must not be negative");
        }
    }
}
