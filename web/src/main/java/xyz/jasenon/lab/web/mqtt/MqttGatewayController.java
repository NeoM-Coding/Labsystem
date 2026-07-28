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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/mqtt/gateways")
@Traced("mqtt-gateway-web")
@Tag(name = "MQTT网关", description = "管理系统接入的 MQTT RS485 网关")
public class MqttGatewayController {

    @DubboReference(check = false)
    private MqttGatewayCRUD gatewayCRUD;

    @GetMapping
    @Operation(summary = "查询MQTT网关", description = "返回当前系统登记的全部 MQTT RS485 网关。")
    public DiyResponseEntity<R<List<RS485Gateway>>> list(
            @RequestParam(required = false) List<String> laboratoryIds) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> gatewayCRUD.listByLaboratories(laboratoryIds))
        ));
    }

    @GetMapping("/{gatewayId}")
    @Operation(summary = "查询MQTT网关详情", description = "根据网关 ID 查询 MQTT 网关配置。")
    public DiyResponseEntity<R<RS485Gateway>> get(@PathVariable String gatewayId) {
        return DiyResponseEntity.of(R.success(RpcClient.call(() -> gatewayCRUD.get(gatewayId))));
    }

    @PostMapping
    @Operation(summary = "创建MQTT网关", description = "登记新的 MQTT RS485 网关并注册运行时连接。")
    public DiyResponseEntity<R<RS485Gateway>> create(@RequestBody RS485Gateway gateway) {
        return DiyResponseEntity.of(R.success(RpcClient.call(() -> gatewayCRUD.create(gateway))));
    }

    @PutMapping("/{gatewayId}")
    @Operation(summary = "修改MQTT网关", description = "根据网关 ID 更新 MQTT 网关配置并刷新运行时连接。")
    public DiyResponseEntity<R<RS485Gateway>> update(
            @PathVariable String gatewayId,
            @RequestBody RS485Gateway gateway) {
        return DiyResponseEntity.of(R.success(
                RpcClient.call(() -> gatewayCRUD.update(gatewayId, gateway))
        ));
    }

    @DeleteMapping("/{gatewayId}")
    @Operation(summary = "删除MQTT网关", description = "删除指定 MQTT 网关并注销相关运行时资源。")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String gatewayId) {
        RpcClient.run(() -> gatewayCRUD.delete(gatewayId));
        return DiyResponseEntity.of(R.success());
    }
}
