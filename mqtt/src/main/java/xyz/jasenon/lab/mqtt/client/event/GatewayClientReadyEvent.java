package xyz.jasenon.lab.mqtt.client.event;

import java.util.Objects;

public final class GatewayClientReadyEvent {

    private final String gatewayId;

    public GatewayClientReadyEvent(String gatewayId) {
        this.gatewayId = Objects.requireNonNull(gatewayId, "gatewayId");
    }

    public String getGatewayId() {
        return gatewayId;
    }
}
