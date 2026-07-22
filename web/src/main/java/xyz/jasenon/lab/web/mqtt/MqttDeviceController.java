package xyz.jasenon.lab.web.mqtt;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "MQTT设备", description = "管理接入 MQTT 网关的设备及其轮询状态")
public class MqttDeviceController {

    @DubboReference(check = false)
    private MqttDeviceCRUD deviceCRUD;

    @DubboReference(check = false)
    private MqttPollCo pollControl;

    @GetMapping
    @Operation(summary = "查询MQTT设备", description = "查询设备列表，可按 MQTT 网关和实验室进行筛选。")
    public DiyResponseEntity<R<List<Device>>> list(
            @RequestParam(required = false) String gatewayId,
            @RequestParam(required = false) String laboratoryId) {
        return DiyResponseEntity.of(R.success(deviceCRUD.list(gatewayId, laboratoryId)));
    }

    @GetMapping("/{deviceId}")
    @Operation(summary = "查询MQTT设备详情", description = "根据设备 ID 查询设备配置及当前登记信息。")
    public DiyResponseEntity<R<Device>> get(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(deviceCRUD.get(deviceId)));
    }

    @PostMapping
    @Operation(summary = "创建MQTT设备", description = "登记一台 MQTT 设备，并同步注册其运行时轮询能力。")
    public DiyResponseEntity<R<Device>> create(@RequestBody Device device) {
        return DiyResponseEntity.of(R.success(deviceCRUD.create(device)));
    }

    @PutMapping("/{deviceId}")
    @Operation(summary = "修改MQTT设备", description = "根据设备 ID 更新设备配置，并同步刷新 MQTT 运行时状态。")
    public DiyResponseEntity<R<Device>> update(@PathVariable String deviceId, @RequestBody Device device) {
        return DiyResponseEntity.of(R.success(deviceCRUD.update(deviceId, device)));
    }

    @DeleteMapping("/{deviceId}")
    @Operation(summary = "删除MQTT设备", description = "删除指定设备，并注销其轮询和运行时资源。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String deviceId) {
        deviceCRUD.delete(deviceId);
        return DiyResponseEntity.of(R.success());
    }

    @PutMapping("/{deviceId}/polling")
    @Operation(summary = "开启设备轮询", description = "启用指定设备的 MQTT 状态轮询任务。")
    public DiyResponseEntity<R<Pair<Boolean, String>>> enablePolling(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(pollControl.enable(deviceId)));
    }

    @DeleteMapping("/{deviceId}/polling")
    @Operation(summary = "关闭设备轮询", description = "停止指定设备的 MQTT 状态轮询任务。")
    public DiyResponseEntity<R<Pair<Boolean, String>>> disablePolling(@PathVariable String deviceId) {
        return DiyResponseEntity.of(R.success(pollControl.disable(deviceId)));
    }
}
