package xyz.jasenon.lab.engine.event;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EventTable<V> {

    private final ConcurrentHashMap<EventKey, V> table = new ConcurrentHashMap<>();

    public Optional<V> get(EventKey key) {
        return Optional.ofNullable(table.get(key));
    }

    public V getOrDefault(EventKey key, V defaultValue) {
        return table.getOrDefault(key, defaultValue);
    }

    public V put(EventKey key, V value) {
        return table.put(key, value);
    }

    public V remove(EventKey key) {
        return table.remove(key);
    }

    public V computeIfAbsent(EventKey key, Function<EventKey, V> mappingFunction) {
        return table.computeIfAbsent(key, mappingFunction);
    }

    public boolean containsKey(EventKey key) {
        return table.containsKey(key);
    }

    public Collection<V> values() {
        return table.values();
    }

    public Set<EventKey> keys() {
        return table.keySet();
    }

    public void clear() {
        table.clear();
    }
}
