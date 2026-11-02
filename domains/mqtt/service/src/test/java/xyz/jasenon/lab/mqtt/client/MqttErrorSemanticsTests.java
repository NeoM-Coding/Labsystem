package xyz.jasenon.lab.mqtt.client;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.config.MqttOptions;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MqttErrorSemanticsTests {
    @Test
    void syncTimeoutAndWorkerTimeoutShareTheSameBusinessCode() {
        for (boolean workerTimeout : new boolean[] { false, true }) {
            TaskHelper helper = mock(TaskHelper.class);
            MqttTask task = prepared("device-1");
            PendingRequest<MqttTask> pending = new PendingRequest<>(task, PendingRequest.Type.USER, 1);
            if (workerTimeout) pending.getFuture().completeExceptionally(new TimeoutException());
            when(task.decorate()).thenReturn(pending);
            when(helper.help(any(MqttTaskDto.class))).thenReturn(task);
            try (var client = new RegisteredClient()) {
                BusinessException error = assertThrows(BusinessException.class, () -> manager(helper).syncSend(new MqttTaskDto()));
                assertEquals(130001, error.getCode());
                assertEquals(504, error.getHttpStatus());
                assertEquals("设备响应超时，请检查设备连接", error.getMessage());
            }
        }
    }

    @Test
    void asynchronousWorkerTimeoutReturnsAnErrorValue() {
        TaskHelper helper = mock(TaskHelper.class);
        MqttTask task = prepared("device-1");
        PendingRequest<MqttTask> pending = new PendingRequest<>(task, PendingRequest.Type.USER, 1);
        pending.getFuture().completeExceptionally(new TimeoutException());
        when(task.decorate()).thenReturn(pending);
        when(helper.help(any(MqttTaskDto.class))).thenReturn(task);
        try (var client = new RegisteredClient()) {
            var result = manager(helper).asyncSendFromRuleEngine(new MqttTaskDto()).join();
            assertFalse(result.successful());
            assertEquals(130001, result.error().code());
        }
    }

    @Test
    void batchResultsKeepSuccessAndPerDeviceErrorCodes() {
        TaskHelper helper = mock(TaskHelper.class);
        MqttTask good = prepared("good");
        MqttTask slow = prepared("slow");
        var completed = new PendingRequest<>(good, PendingRequest.Type.USER, 1);
        completed.getFuture().complete(good);
        when(good.decorate()).thenReturn(completed);
        var timedOut = new PendingRequest<>(slow, PendingRequest.Type.USER, 1);
        timedOut.getFuture().completeExceptionally(new TimeoutException());
        when(slow.decorate()).thenReturn(timedOut);
        when(helper.help(any(MqttTaskDto.class))).thenAnswer(call ->
                "good".equals(((MqttTaskDto) call.getArgument(0)).getDeviceId()) ? good : slow);
        try (var client = new RegisteredClient()) {
            var results = manager(helper).multiSend(new MqttMultiTaskDto(
                    CommandLine.OPEN_LIGHT, new int[0], DeviceType.Light, List.of("good", "slow"))).data();
            assertTrue(results.get(0).success());
            assertEquals(200, results.get(0).code());
            assertFalse(results.get(1).success());
            assertEquals(130001, results.get(1).code());
        }
    }

    private static class RegisteredClient implements AutoCloseable {
        RegisteredClient() {
            var client = mock(AbstractSysClient.class);
            org.springframework.test.util.ReflectionTestUtils.setField(client, "gatewayId", "gateway-1");
            ClientsRuntime.register(client);
        }
        public void close() { ClientsRuntime.remove("gateway-1"); }
    }

    private MqttTask prepared(String id) {
        MqttTask task = mock(MqttTask.class);
        when(task.getDeviceId()).thenReturn(id);
        when(task.getGatewayId()).thenReturn("gateway-1");
        when(task.getLaboratoryId()).thenReturn("lab-1");
        return task;
    }
    private SysClientManager manager(TaskHelper helper) {
        var scope = mock(VisibleLaboratoryScope.class);
        when(scope.resolve(List.of("lab-1"))).thenReturn(List.of("lab-1"));
        return new SysClientManager(helper, mock(GatewayHelper.class), mock(ApplicationEventPublisher.class), new MqttOptions(), scope);
    }
}
