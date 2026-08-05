package xyz.jasenon.lab.mqtt.client.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class GatewayClientsInitialRebuildCompletedEvent {

    private final Set<String> gatewayIds;

    public GatewayClientsInitialRebuildCompletedEvent(Set<String> gatewayIds) {
        // 只读set
        this.gatewayIds = Collections.unmodifiableSet(new HashSet<>(gatewayIds));
    }

    public Set<String> getGatewayIds() {
        return gatewayIds;
    }
}
