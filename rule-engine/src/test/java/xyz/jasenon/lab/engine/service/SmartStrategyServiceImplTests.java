package xyz.jasenon.lab.engine.service;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.persistence.itfc.RuntimePersist;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartStrategyServiceImplTests {

    @Test
    void createReturnsPersistedRevision() {
        InMemoryRuntimePersist persist = new InMemoryRuntimePersist();
        SmartStrategyServiceImpl service = new SmartStrategyServiceImpl(persist);

        RuntimeRevision created = service.create(new SmartStrategyCreate(revision(true)));

        assertTrue(created.isEnabled());
        assertTrue(persist.values.containsKey("runtime-1"));
    }

    @Test
    void statusChangeDelegatesToPersistenceLifecycle() {
        InMemoryRuntimePersist persist = new InMemoryRuntimePersist();
        persist.register(revision(true));
        SmartStrategyServiceImpl service = new SmartStrategyServiceImpl(persist);

        RuntimeRevision disabled = service.changeStatus(
                new SmartStrategyStatusChange("runtime-1", false)
        );

        assertFalse(disabled.isEnabled());
    }

    private RuntimeRevision revision(boolean enabled) {
        return new RuntimeRevision("runtime-1", enabled, null, null, List.of(), List.of(), List.of());
    }

    private static final class InMemoryRuntimePersist implements RuntimePersist {
        private final Map<String, RuntimeRevision> values = new LinkedHashMap<>();

        @Override
        public boolean register(RuntimeRevision revision) {
            return values.putIfAbsent(revision.runtimeId(), revision) == null;
        }

        @Override
        public boolean update(String runtimeId, RuntimeRevision revision) {
            if (!values.containsKey(runtimeId)) return false;
            values.put(runtimeId, revision);
            return true;
        }

        @Override
        public boolean remove(String runtimeId) {
            return values.remove(runtimeId) != null;
        }

        @Override
        public boolean enable(String runtimeId) {
            return changeStatus(runtimeId, true);
        }

        @Override
        public boolean disable(String runtimeId) {
            return changeStatus(runtimeId, false);
        }

        @Override
        public RuntimeRevision get(String runtimeId) {
            return values.get(runtimeId);
        }

        @Override
        public List<RuntimeRevision> fetch() {
            return List.copyOf(values.values());
        }

        private boolean changeStatus(String runtimeId, boolean enabled) {
            RuntimeRevision current = values.get(runtimeId);
            if (current == null) return false;
            values.put(runtimeId, current.withEnabled(enabled));
            return true;
        }
    }
}
