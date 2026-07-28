package xyz.jasenon.lab.api.mqtt;

import xyz.jasenon.lab.api.mqtt.dto.DeviceTelemetrySnapshot;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

public interface MqttTelemetryQuery {

    RpcResult<List<DeviceTelemetrySnapshot>> snapshots(List<String> laboratoryIds);
}
