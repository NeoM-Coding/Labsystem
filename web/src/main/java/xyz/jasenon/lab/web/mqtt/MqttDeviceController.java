package xyz.jasenon.lab.web.mqtt;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.api.mqtt.MqttDeviceCRUD;
import xyz.jasenon.lab.api.mqtt.MqttPollCo;
import xyz.jasenon.lab.common.util.Pair;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.device.model.Device;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/mqtt/devices")
@Traced("mqtt-device-web")
public class MqttDeviceController {

    @DubboReference(check = false)
    private MqttDeviceCRUD deviceCRUD;

    @DubboReference(check = false)
    private MqttPollCo pollControl;

    @GetMapping
    public DiyResponseEntity<R<List<Device>>> list(
            @RequestParam(required = false) String gatewayId,
            @RequestParam(required = false) String laboratoryId) {
        return DiyResponseEntity.of(R.success(deviceCRUD.list(gatewayId, laboratoryId)));
    }

    @GetMapping("/{deviceId}")
    public DiyResponseEntity<R<Device>> get(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(deviceCRUD.get(deviceId)));
    }

    @PostMapping
    public DiyResponseEntity<R<Device>> create(@RequestBody Device device) {
        return DiyResponseEntity.of(R.success(deviceCRUD.create(device)));
    }

    @PutMapping("/{deviceId}")
    public DiyResponseEntity<R<Device>> update(@PathVariable String deviceId, @RequestBody Device device) {
        return DiyResponseEntity.of(R.success(deviceCRUD.update(deviceId, device)));
    }

    @DeleteMapping("/{deviceId}")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String deviceId) {
        deviceCRUD.delete(deviceId);
        return DiyResponseEntity.of(R.success());
    }

    @PutMapping("/{deviceId}/polling")
    public DiyResponseEntity<R<Pair<Boolean, String>>> enablePolling(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(pollControl.enable(deviceId)));
    }

    @DeleteMapping("/{deviceId}/polling")
    public DiyResponseEntity<R<Pair<Boolean, String>>> disablePolling(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(pollControl.disable(deviceId)));
    }
}
