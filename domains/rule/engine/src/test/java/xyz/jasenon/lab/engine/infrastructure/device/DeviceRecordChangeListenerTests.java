package xyz.jasenon.lab.engine.infrastructure.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.plan.RuntimePlan.ActionGroup;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Binding;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Lifetime;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Predicate;
import xyz.jasenon.lab.engine.scheduling.EngineOptions;
import xyz.jasenon.lab.engine.scheduling.TimeScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceRecordChangeListenerTests {
    @Test
    void registrationNeverReplaysAnEarlierObservation() {
        EngineOptions options = new EngineOptions();
        options.setWorkers(1);
        try (var timers = new TimeScheduler(); var actions = new ActionDispatcher((r, g, a) -> {
            throw new AssertionError("no actions");
        }, n -> {
        }, options);
             var engine = new Engine(actions, timers, options)) {
            var listener = new DeviceRecordChangeListener(engine, null, new ObjectMapper());
            var observation = new DeviceRecordSnapshotEvent(DeviceType.AirCondition, "ac-1", Map.of("opened", "true"), Instant.now());
            listener.accept(observation);
            var key = new DeviceEventKey(DeviceType.AirCondition, "ac-1", "opened");
            var plan = new RuntimePlan("r", new Lifetime(null, null), Map.of("d", List.of(new Predicate("p", key, Operator.EQ, "true", LogicType.AND))),
                    Map.of("t", List.of()), Map.of("a", new ActionGroup("a", List.of())), List.of(new Binding("b", "d", "t", "a", TriggerMode.EDGE)));
            engine.register(plan);
            assertFalse(engine.inspect("r").roots().get("b"));
            listener.accept(observation);
            assertTrue(engine.inspect("r").roots().get("b"));
        }
    }
}
