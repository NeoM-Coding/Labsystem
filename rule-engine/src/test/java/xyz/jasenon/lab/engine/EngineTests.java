package xyz.jasenon.lab.engine;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.runtime.RuntimeExecutor;
import xyz.jasenon.lab.engine.runtime.Runtime;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTests {

    @Test
    void enqueuesRuntimeWhenRegisteredEventChangesRoot() {
        Engine engine = new Engine();
        Runtime runtime = new Runtime("runtime-1");
        runtime.registerActionGroup(new ActionGroup("group-1", chain("ac-1", "roomTemperature", Operator.GT, "26")));
        engine.register(runtime);

        engine.accept(new DeviceEvent(DeviceType.AirCondition, "ac-1", "roomTemperature", "27", Instant.now()));
        engine.accept(new DeviceEvent(DeviceType.AirCondition, "ac-1", "roomTemperature", "28", Instant.now()));

        assertEquals(1, engine.readySize());
        assertEquals("runtime-1", engine.pollReady());
        assertNull(engine.pollReady());
    }

    @Test
    void ignoresUnregisteredEvents() {
        Engine engine = new Engine();
        engine.accept(new DeviceEvent(DeviceType.AirCondition, "ac-1", "roomTemperature", "27", Instant.now()));
        assertEquals(0, engine.readySize());
    }

    @Test
    void drainsReadyRuntimeAndExecutesSatisfiedActionGroups() throws InterruptedException {
        RecordingRuntimeExecutor executor = new RecordingRuntimeExecutor(2);
        Engine engine = new Engine(executor, false);
        Runtime runtime = new Runtime("runtime-1");
        runtime.registerActionGroup(new ActionGroup("high-temperature", chain("ac-1", "roomTemperature", Operator.GT, "26")));
        runtime.registerActionGroup(new ActionGroup("normal-temperature", chain("ac-1", "roomTemperature", Operator.ST, "30")));
        runtime.registerActionGroup(new ActionGroup("too-high-temperature", chain("ac-1", "roomTemperature", Operator.GT, "30")));
        engine.register(runtime);

        engine.accept(new DeviceEvent(DeviceType.AirCondition, "ac-1", "roomTemperature", "27", Instant.now()));
        assertEquals(1, engine.readySize());

        engine.drainReady();

        assertTrue(executor.await());
        assertEquals(List.of("runtime-1:high-temperature", "runtime-1:normal-temperature"), executor.executed);
        assertEquals(0, engine.activeReadySize());
        engine.stop();
    }

    private static EvalNode chain(String deviceId, String field, Operator operator, String value) {
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
        node.setResult(false);
        dummy.setNext(node);
        return dummy;
    }

    private static class RecordingRuntimeExecutor implements RuntimeExecutor {

        private final CountDownLatch latch;
        private final List<String> executed = new CopyOnWriteArrayList<>();

        RecordingRuntimeExecutor(int expectedExecutions) {
            this.latch = new CountDownLatch(expectedExecutions);
        }

        @Override
        public void execute(Runtime runtime, ActionGroup actionGroup) {
            executed.add(runtime.getRuntimeId() + ":" + actionGroup.getActionGroupId());
            latch.countDown();
        }

        boolean await() throws InterruptedException {
            return latch.await(2, TimeUnit.SECONDS);
        }
    }
}
