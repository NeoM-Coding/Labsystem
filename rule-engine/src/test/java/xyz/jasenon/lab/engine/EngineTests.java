package xyz.jasenon.lab.engine;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTests {

    @Test
    void refreshesMatchedRuntimeAndDelegatesScheduling() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        Runtime runtime = runtime("runtime-1");
        engine.register(runtime);

        engine.accept(event("27"));

        assertEquals(List.of("runtime-1"), scheduler.scheduledRuntimeIds);
        assertTrue(runtime.getActionGroups().get(0).getRoot().isOk());
    }

    @Test
    void ignoresUnregisteredEvents() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);

        engine.accept(event("27"));

        assertTrue(scheduler.scheduledRuntimeIds.isEmpty());
    }

    @Test
    void removeClearsEventIndexAndCancelsRuntimeScheduling() {
        RecordingRuntimeScheduler scheduler = new RecordingRuntimeScheduler();
        Engine engine = new Engine(scheduler);
        engine.register(runtime("runtime-1"));

        engine.remove("runtime-1");
        engine.accept(event("27"));

        assertEquals(List.of("runtime-1"), scheduler.cancelledRuntimeIds);
        assertTrue(scheduler.scheduledRuntimeIds.isEmpty());
    }

    private static Runtime runtime(String runtimeId) {
        Runtime runtime = new Runtime(runtimeId);
        runtime.registerActionGroup(new ActionGroup(
                "high-temperature",
                chain("ac-1", "roomTemperature", Operator.GT, "26", false)
        ));
        return runtime;
    }

    private static DeviceEvent event(String value) {
        return new DeviceEvent(
                DeviceType.AirCondition,
                "ac-1",
                "roomTemperature",
                value,
                Instant.now()
        );
    }

    private static EvalNode chain(
            String deviceId,
            String field,
            Operator operator,
            String value,
            boolean initialResult
    ) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);

        EvalNode node = new EvalNode();
        node.setNodeId("node-1");
        node.setDeviceId(deviceId);
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setLogicToPrev(LogicType.AND);
        node.setResult(initialResult);
        dummy.setNext(node);
        return dummy;
    }

    private static class RecordingRuntimeScheduler implements RuntimeScheduler {

        private final List<String> scheduledRuntimeIds = new ArrayList<>();
        private final List<String> cancelledRuntimeIds = new ArrayList<>();

        @Override
        public void schedule(Runtime runtime) {
            scheduledRuntimeIds.add(runtime.getRuntimeId());
        }

        @Override
        public void cancel(String runtimeId) {
            cancelledRuntimeIds.add(runtimeId);
        }
    }
}
