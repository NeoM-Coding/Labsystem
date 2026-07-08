package xyz.jasenon.lab.engine.test;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.listener.DeviceRecordChangeListener;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.time.TimePointCondition;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SimpleTestTests {

    @Test
    void registersAllTimeConditionVariantsWithSharedDeviceCondition() {
        Engine engine = mock(Engine.class);
        DeviceRecordChangeListener listener = mock(DeviceRecordChangeListener.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty(
                        "lab.rule-engine.simple-test.source-device-id",
                        "air-condition-31-6"
                )
                .withProperty("lab.rule-engine.simple-test.time-point-delay-seconds", "60");
        SimpleTest simpleTest = new SimpleTest(engine, listener, environment);

        simpleTest.registerSimpleRuntimeThenAcceptDeviceRecordToStartRuntimeFunctionTest();

        ArgumentCaptor<Runtime> runtimeCaptor = ArgumentCaptor.forClass(Runtime.class);
        verify(engine).register(runtimeCaptor.capture());
        verifyNoMoreInteractions(engine);
        verify(listener).replay(
                xyz.jasenon.lab.common.model.device.DeviceType.AirCondition,
                "air-condition-31-6"
        );
        verifyNoMoreInteractions(listener);

        Runtime runtime = runtimeCaptor.getValue();
        assertEquals(3, runtime.getActionGroups().size());
        assertSame(
                runtime.getActionGroups().get(0).getDeviceConditionGroup(),
                runtime.getActionGroups().get(1).getDeviceConditionGroup()
        );
        assertSame(
                runtime.getActionGroups().get(0).getDeviceConditionGroup(),
                runtime.getActionGroups().get(2).getDeviceConditionGroup()
        );

        assertTrue(runtime.getActionGroups().get(0)
                .getTimeConditionGroup().conditions().get(0) instanceof TimeWindowCondition);
        assertTrue(runtime.getActionGroups().get(1)
                .getTimeConditionGroup().conditions().get(0) instanceof TimeWindowCondition);
        assertTrue(runtime.getActionGroups().get(2)
                .getTimeConditionGroup().conditions().get(0) instanceof TimePointCondition);

        assertTrue(runtime.getActionGroups().get(0).getActions().stream()
                .anyMatch(ControlAction.class::isInstance));
        assertTrue(runtime.getActionGroups().stream()
                .flatMap(group -> group.getActions().stream())
                .anyMatch(ReportAction.class::isInstance));
    }
}
