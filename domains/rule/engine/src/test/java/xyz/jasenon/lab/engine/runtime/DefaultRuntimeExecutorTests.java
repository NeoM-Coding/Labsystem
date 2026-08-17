package xyz.jasenon.lab.engine.runtime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.jasenon.lab.api.mqtt.MqttRuleIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.RuleEngineApplication;
import xyz.jasenon.lab.engine.action.*;
import xyz.jasenon.lab.engine.definition.RuntimePlan;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultRuntimeExecutorTests {

    @Test
    void controlActionUsesAsyncMqttAndRecordsSuccess() {
        MqttRuleIo mqttIo = mock(MqttRuleIo.class);
        ActionExecutionTracker tracker = new ActionExecutionTracker();
        DefaultRuntimeExecutor executor = new DefaultRuntimeExecutor(tracker, mqttIo);
        RuntimeActionGroup actionGroup = actionGroup("group-1");
        Runtime runtime = runtime("runtime-1", actionGroup);
        MqttTaskDto task = task("ac-1");
        MqttResponseDto response = new MqttResponseDto();
        response.setGatewayId("gateway-1");
        when(mqttIo.asyncSend(task))
                .thenReturn(CompletableFuture.completedFuture(RpcResult.success(response)));

        ActionExecutionResult result = executor.execute(
                runtime,
                actionGroup,
                new ControlAction("group-1", task)
        ).join();

        assertEquals(ActionExecutionResult.Status.SUCCESS, result.status());
        assertEquals(1, tracker.successCount());
        assertEquals(0, tracker.failureCount());
        verify(mqttIo).asyncSend(task);
    }

    @Test
    void controlActionRecordsFailureDetails() {
        MqttRuleIo mqttIo = mock(MqttRuleIo.class);
        ActionExecutionTracker tracker = new ActionExecutionTracker();
        DefaultRuntimeExecutor executor = new DefaultRuntimeExecutor(tracker, mqttIo);
        RuntimeActionGroup actionGroup = actionGroup("group-1");
        Runtime runtime = runtime("runtime-1", actionGroup);
        MqttTaskDto task = task("ac-1");
        when(mqttIo.asyncSend(task)).thenReturn(CompletableFuture.failedFuture(new TimeoutException("timeout")));

        ActionExecutionResult result = executor.execute(
                runtime,
                actionGroup,
                new ControlAction("group-1", task)
        ).join();

        assertEquals(ActionExecutionResult.Status.FAILED, result.status());
        assertEquals(0, tracker.successCount());
        assertEquals(1, tracker.failureCount());
        assertEquals(1, tracker.recentFailures().size());
        ActionFailure failure = tracker.recentFailures().get(0);
        assertEquals("runtime-1", failure.runtimeId());
        assertEquals("group-1", failure.actionGroupId());
        assertEquals("ac-1", failure.targetId());
        assertEquals(TimeoutException.class.getName(), failure.errorType());
        assertEquals("timeout", failure.message());
    }

    @Test
    void reportActionKeepsNotificationSkeleton() {
        MqttRuleIo mqttIo = mock(MqttRuleIo.class);
        ActionExecutionTracker tracker = new ActionExecutionTracker();
        DefaultRuntimeExecutor executor = new DefaultRuntimeExecutor(tracker, mqttIo);
        ReportAction action = new ReportAction(
                "group-1",
                List.of("user-1", "user-2"),
                EnumSet.of(ReportAction.ReportType.SMS, ReportAction.ReportType.SMTP),
                "temperature alarm"
        );

        ActionExecutionResult result = executor.execute(
                runtime("runtime-1", actionGroup("group-1")),
                actionGroup("group-1"),
                action
        ).join();

        assertEquals(ActionExecutionResult.Status.NOT_IMPLEMENTED, result.status());
        assertTrue(result.message().contains("not implemented"));
        assertEquals(0, tracker.successCount());
        assertEquals(0, tracker.failureCount());
    }

    @Nested
    @SpringBootTest(
            classes = {
                    RuleEngineApplication.class,
                    MqttIoTestConfiguration.class
            },
            webEnvironment = SpringBootTest.WebEnvironment.NONE,
            properties = {
                    "lab.redis.enabled=false",
                    "lab.rule-engine.persistence.enabled=false",
                    "dubbo.registry.address=N/A",
                    "dubbo.config-center.address=N/A",
                    "spring.profiles.active=test",
                    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                    "fun.uid.assigner-mode=none"
            }
    )
    class SpringChainTests {

        @Autowired
        private Engine springEngine;

        @Autowired
        private ActionExecutionTracker springTracker;

        @Autowired
        private InProcessMqttIo springMqttIo;

        @Test
        void sendsControlActionsAndTracksFailures() throws InterruptedException {
            String runtimeId = "spring-mqtt-runtime";
            String actionGroupId = "spring-mqtt-actions";
            RuntimePlan runtime = conditionalPlan(
                    runtimeId,
                    actionGroupId,
                    "source-ac",
                    List.of(
                            new ControlAction(actionGroupId, task("target-ok")),
                            new ControlAction(actionGroupId, task("target-fail"))
                    )
            );

            springMqttIo.reset();
            long successBefore = springTracker.successCount();
            long failureBefore = springTracker.failureCount();
            springEngine.register(runtime);

            try {
                springEngine.accept(new DeviceEvent(
                        DeviceType.AirCondition,
                        "source-ac",
                        "roomTemperature",
                        "28",
                        Instant.now()
                ));

                assertTrue(await(() ->
                        springTracker.successCount() == successBefore + 1
                                && springTracker.failureCount() == failureBefore + 1
                ));
                assertTrue(springMqttIo.receivedDeviceIds().containsAll(List.of("target-ok", "target-fail")));
                assertTrue(springMqttIo.executionThreadNames().stream()
                        .allMatch(name -> name.startsWith("test-mqtt-io")));

                ActionFailure failure = springTracker.recentFailures().stream()
                        .filter(item -> runtimeId.equals(item.runtimeId()))
                        .findFirst()
                        .orElseThrow();
                assertEquals(actionGroupId, failure.actionGroupId());
                assertEquals("target-fail", failure.targetId());
                assertEquals(IllegalStateException.class.getName(), failure.errorType());
                assertEquals("simulated mqtt send failure", failure.message());
            } finally {
                springEngine.remove(runtimeId);
            }
        }
    }

    private static RuntimeActionGroup actionGroup(String actionGroupId) {
        return new RuntimeActionGroup(
                actionGroupId,
                "always",
                TimeConditionGroup.always("always-time"),
                List.of()
        );
    }

    private static RuntimePlan conditionalPlan(
            String runtimeId,
            String actionGroupId,
            String deviceId,
            List<Action> actions
    ) {
        EvalNode condition = new EvalNode();
        condition.setNodeId("temperature-condition");
        condition.setDeviceId(deviceId);
        condition.setDeviceType(DeviceType.AirCondition);
        condition.setField("roomTemperature");
        condition.setOperator(Operator.GT);
        condition.setValue("26");
        condition.setLogicToPrev(LogicType.AND);
        condition.setResult(false);
        TimeConditionGroup always = TimeConditionGroup.always("always-time");
        DeviceEventKey key = new DeviceEventKey(
                DeviceType.AirCondition,
                deviceId,
                "roomTemperature"
        );
        return new RuntimePlan(
                runtimeId,
                RuntimeLifetime.always(),
                java.util.Map.of("temperature", condition),
                java.util.Set.of(),
                java.util.Map.of(always.getGroupId(), always),
                List.of(new RuntimeActionGroup(
                        actionGroupId,
                        "temperature",
                        always,
                        actions
                )),
                java.util.Set.of(key)
        );
    }

    private static Runtime runtime(String runtimeId, RuntimeActionGroup actionGroup) {
        xyz.jasenon.lab.engine.eval.v2.EvalForest forest = new xyz.jasenon.lab.engine.eval.v2.EvalForest();
        TimeConditionGroup timeGroup = actionGroup.timeConditionGroup();
        return new Runtime(
                runtimeId,
                RuntimeLifetime.always(),
                forest.registerRuntime(runtimeId, java.util.Map.of(), java.util.Set.of("always")),
                java.util.Map.of(timeGroup.getGroupId(), timeGroup),
                List.of(new RuntimeActionGroup(
                        actionGroup.actionGroupId(),
                        "always",
                        timeGroup,
                        actionGroup.actions()
                ))
        );
    }

    private static MqttTaskDto task(String deviceId) {
        MqttTaskDto task = new MqttTaskDto();
        task.setType(DeviceType.AirCondition);
        task.setDeviceId(deviceId);
        return task;
    }

    private static boolean await(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25);
        }
        return false;
    }

    @TestConfiguration
    static class MqttIoTestConfiguration {

        @Bean(destroyMethod = "close")
        InProcessMqttIo inProcessMqttIo() {
            return new InProcessMqttIo();
        }

        @Bean
        @Primary
        RuntimeExecutor springRuntimeExecutor(
                ActionExecutionTracker tracker,
                InProcessMqttIo mqttIo
        ) {
            DefaultRuntimeExecutor delegate = new DefaultRuntimeExecutor(tracker, mqttIo);
            // Keep the delegate outside Spring's Dubbo field post-processing so this test
            // replaces only the external transport boundary.
            return delegate::execute;
        }
    }

    /**
     * CI-safe MQTT boundary: it performs real asynchronous work without requiring
     * a broker, registry or separately deployed MQTT application.
     */
    static class InProcessMqttIo implements MqttRuleIo, AutoCloseable {

        private final Queue<String> receivedDeviceIds = new ConcurrentLinkedQueue<>();
        private final Queue<String> executionThreadNames = new ConcurrentLinkedQueue<>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "test-mqtt-io");
            thread.setDaemon(true);
            return thread;
        });

        @Override
        public CompletableFuture<RpcResult<MqttResponseDto>> asyncSend(MqttTaskDto task) {
            return CompletableFuture.supplyAsync(() -> {
                receivedDeviceIds.add(task.getDeviceId());
                executionThreadNames.add(Thread.currentThread().getName());
                if ("target-fail".equals(task.getDeviceId())) {
                    throw new IllegalStateException("simulated mqtt send failure");
                }

                MqttResponseDto response = new MqttResponseDto();
                response.setGatewayId("test-gateway");
                response.setPayload(new int[]{1});
                return RpcResult.success(response);
            }, executor);
        }

        List<String> receivedDeviceIds() {
            return List.copyOf(receivedDeviceIds);
        }

        List<String> executionThreadNames() {
            return List.copyOf(executionThreadNames);
        }

        void reset() {
            receivedDeviceIds.clear();
            executionThreadNames.clear();
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
