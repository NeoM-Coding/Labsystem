package xyz.jasenon.lab.engine.execution;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.action.ActionExecutionTracker;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.PollAction;
import xyz.jasenon.lab.engine.plan.RuntimePlan.ActionGroup;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Lifetime;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultRuntimeExecutorTests {
    final ExecutionContext context = new ExecutionContext("r", 1, new Lifetime(null, null), new AtomicBoolean(true));
    final ActionGroup group = new ActionGroup("a", List.of());

    ControlAction control() {
        return new ControlAction("a", new MqttTaskDto(CommandLine.OPEN_AIR_CONDITION_RS485, new int[0], DeviceType.AirCondition, "ac-1"));
    }

    @Test
    void controlPreservesAsyncResultAndTracksSuccess() {
        var tracker = new ActionExecutionTracker();
        var transport = new CompletableFuture<RpcResult<MqttResponseDto>>();
        var executor = new DefaultRuntimeExecutor(tracker, task -> transport);
        var result = executor.execute(context, group, control());
        assertFalse(result.isDone());
        transport.complete(RpcResult.success(new MqttResponseDto()));
        assertEquals(ActionExecutionResult.Status.SUCCESS, result.join().status());
        assertEquals(1, tracker.successCount());
    }

    @Test
    void transportFailureBecomesStructuredFailure() {
        var tracker = new ActionExecutionTracker();
        var executor = new DefaultRuntimeExecutor(tracker, task -> CompletableFuture.failedFuture(new IllegalStateException("offline")));
        assertEquals(ActionExecutionResult.Status.FAILED, executor.execute(context, group, control()).join().status());
        assertEquals(1, tracker.failureCount());
    }

    @Test
    void pollDoesNotWaitForDeviceResponse() {
        var tracker = new ActionExecutionTracker();
        var never = new CompletableFuture<RpcResult<MqttResponseDto>>();
        var executor = new DefaultRuntimeExecutor(tracker, task -> never);
        assertEquals(ActionExecutionResult.Status.SUCCESS, executor.execute(context, group, new PollAction(DeviceType.AirCondition, "ac-1")).join().status());
        assertFalse(never.isDone());
    }
}
