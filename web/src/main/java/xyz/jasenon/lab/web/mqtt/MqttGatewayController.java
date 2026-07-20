package xyz.jasenon.lab.web.mqtt;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/mqtt/gateways")
@Traced("mqtt-gateway-web")
public class MqttGatewayController {

    @DubboReference(check = false)
    private MqttGatewayCRUD gatewayCRUD;

    @GetMapping
    public DiyResponseEntity<R<List<RS485Gateway>>> list() {
        return DiyResponseEntity.of(R.success(gatewayCRUD.list()));
    }

    @GetMapping("/{gatewayId}")
    public DiyResponseEntity<R<RS485Gateway>> get(@PathVariable String gatewayId) {
        return DiyResponseEntity.of(R.success(gatewayCRUD.get(gatewayId)));
    }

    @PostMapping
    public DiyResponseEntity<R<RS485Gateway>> create(@RequestBody RS485Gateway gateway) {
        return DiyResponseEntity.of(R.success(gatewayCRUD.create(gateway)));
    }

    @PutMapping("/{gatewayId}")
    public DiyResponseEntity<R<RS485Gateway>> update(
            @PathVariable String gatewayId,
            @RequestBody RS485Gateway gateway) {
        return DiyResponseEntity.of(R.success(gatewayCRUD.update(gatewayId, gateway)));
    }

    @DeleteMapping("/{gatewayId}")
    public DiyResponseEntity<R<Void>> delete(@PathVariable String gatewayId) {
        gatewayCRUD.delete(gatewayId);
        return DiyResponseEntity.of(R.success());
    }
}
