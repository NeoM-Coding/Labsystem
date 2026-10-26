package xyz.jasenon.lab.engine.scheduling;

import xyz.jasenon.lab.engine.event.*;
import xyz.jasenon.lab.engine.runtime.RuntimeInstance;
import java.util.*;

/** Worker-confined registry and subscription index. Selection never crosses worker ownership. */
final class RuntimeSelector {
    private final Map<String, RuntimeInstance> runtimes = new LinkedHashMap<>();
    private final Map<DeviceEventKey, Set<RuntimeInstance>> subscriptions = new HashMap<>();

    RuntimeInstance find(String id) {
        return runtimes.get(id);
    }

    void install(RuntimeInstance replacement) {
        RuntimeInstance previous = remove(replacement.plan.runtimeId());
        if (previous != null) previous.cancel();
        runtimes.put(replacement.plan.runtimeId(), replacement);
        for (DeviceEventKey key : replacement.plan.requiredEventKeys())
            subscriptions.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(replacement);
    }

    RuntimeInstance remove(String id) {
        RuntimeInstance previous = runtimes.remove(id);
        if (previous == null) return null;
        for (DeviceEventKey key : previous.plan.requiredEventKeys()) {
            Set<RuntimeInstance> targets = subscriptions.get(key);
            if (targets != null) {
                targets.remove(previous);
                if (targets.isEmpty()) subscriptions.remove(key);
            }
        }
        return previous;
    }

    Collection<RuntimeInstance> select(EngineEvent event) {
        if (event instanceof DeviceEvent device)
            return subscriptions.getOrDefault(device.getKey(), Set.of());
        if (event instanceof TimeWindowStatusEvent window) {
            RuntimeInstance runtime = runtimes.get(window.runtimeId());
            if (runtime != null && runtime.generation == window.generation()
                    && runtime.timerToken == window.timerToken())
                return List.of(runtime);
            return List.of();
        }
        throw new IllegalArgumentException("unsupported event: " + event.getClass().getName());
    }

    void close() {
        runtimes.values().forEach(RuntimeInstance::cancel);
        runtimes.clear();
        subscriptions.clear();
    }
}
