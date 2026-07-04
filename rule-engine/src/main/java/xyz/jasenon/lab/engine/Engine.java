package xyz.jasenon.lab.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.EventKey;
import xyz.jasenon.lab.engine.event.EventTable;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;
import xyz.jasenon.lab.engine.runtime.RuntimeTable;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Engine {

    private final EventTable<Set<String>> eventHelper = new EventTable<>();
    private final RuntimeTable runtimeHelper = new RuntimeTable();
    private final RuntimeScheduler runtimeScheduler;

    @Autowired
    public Engine(RuntimeScheduler runtimeScheduler) {
        this.runtimeScheduler = Objects.requireNonNull(runtimeScheduler, "runtimeScheduler");
    }

    public void register(Runtime runtime) {
        runtimeHelper.register(runtime);
        for (EventKey key : runtime.getRoots().keys()) {
            eventHelper.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(runtime.getRuntimeId());
        }
    }

    public void remove(String runtimeId) {
        Runtime runtime = runtimeHelper.remove(runtimeId);
        if (runtime == null) {
            return;
        }
        for (EventKey key : runtime.getRoots().keys()) {
            eventHelper.get(key).ifPresent(runtimeIds -> runtimeIds.remove(runtimeId));
        }
        runtimeScheduler.cancel(runtimeId);
    }

    public void accept(DeviceEvent event) {
        Set<String> runtimeIds = eventHelper.getOrDefault(event.eventKey(), Set.of());
        for (String runtimeId : runtimeIds) {
            runtimeHelper.get(runtimeId).ifPresent(runtime -> accept(runtime, event));
        }
    }

    RuntimeTable runtimeTable() {
        return runtimeHelper;
    }

    private void accept(Runtime runtime, DeviceEvent event) {
        for (var leaf : runtime.leaves(event.eventKey())) {
            leaf.refreshLeaf(event.getValue());
        }
        runtimeScheduler.schedule(runtime);
    }
}
