package xyz.jasenon.lab.engine.runtime;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeTable {

    private final ConcurrentHashMap<String,Runtime> table = new ConcurrentHashMap<>();

    public Runtime register(Runtime runtime) {
        return table.put(runtime.getRuntimeId(), runtime);
    }

    public Optional<Runtime> get(String runtimeId) {
        return Optional.ofNullable(table.get(runtimeId));
    }

    public Runtime remove(String runtimeId) {
        return table.remove(runtimeId);
    }

    public Collection<Runtime> values() {
        return table.values();
    }

    public boolean contains(String runtimeId) {
        return table.containsKey(runtimeId);
    }

    public void clear() {
        table.clear();
    }
}
