package xyz.jasenon.lab.mqtt.client;

import xyz.jasenon.lab.common.command.Task;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ClientsRuntime {

    private static final Map<String, AbstractSysClient<? extends Task>> CLIENTS = new ConcurrentHashMap<>();

    private ClientsRuntime() {
    }

    static void register(AbstractSysClient<? extends Task> client) {
        if (client == null) {
            return;
        }
        CLIENTS.put(client.gatewayId, client);
    }

    static void remove(AbstractSysClient<? extends Task> client) {
        if (client == null) {
            return;
        }
        CLIENTS.remove(client.gatewayId, client);
    }

    static AbstractSysClient<? extends Task> remove(String gatewayId) {
        return CLIENTS.remove(gatewayId);
    }

    static AbstractSysClient<? extends Task> client(String gatewayId) {
        return CLIENTS.get(gatewayId);
    }

    static boolean contains(String gatewayId) {
        return CLIENTS.containsKey(gatewayId);
    }

    static Set<String> clientIds() {
        return new HashSet<>(CLIENTS.keySet());
    }
}
