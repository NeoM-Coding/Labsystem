package xyz.jasenon.lab.mqtt.client;

import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import xyz.jasenon.lab.api.mqtt.MqttRuleIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;
import xyz.jasenon.lab.mqtt.config.MqttOptions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuleMqttIoServiceTests {

    @Test
    void internalRuleCallDoesNotRequireAWebUserContext() {
        TaskHelper taskHelper = mock(TaskHelper.class);
        VisibleLaboratoryScope visibleScope = mock(VisibleLaboratoryScope.class);
        SysClientManager manager = manager(taskHelper, visibleScope);
        MqttTaskDto request = new MqttTaskDto();
        MqttTask task = new MqttTask("missing-rule-gateway-" + UUID.randomUUID());
        task.setLaboratoryId("lab-1");
        when(taskHelper.help(request)).thenReturn(task);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> manager.asyncSendFromRuleEngine(request)
        );

        assertEquals(404, exception.getCode());
        assertEquals("gateway doesn't exist!", exception.getMessage());
        verifyNoInteractions(visibleScope);
    }

    @Test
    void userFacingCallStillRequiresLaboratoryVisibility() {
        TaskHelper taskHelper = mock(TaskHelper.class);
        VisibleLaboratoryScope visibleScope = mock(VisibleLaboratoryScope.class);
        SysClientManager manager = manager(taskHelper, visibleScope);
        MqttTaskDto request = new MqttTaskDto();
        MqttTask task = new MqttTask("missing-user-gateway-" + UUID.randomUUID());
        task.setLaboratoryId("lab-1");
        when(taskHelper.help(request)).thenReturn(task);
        when(visibleScope.resolve(List.of("lab-1")))
                .thenThrow(new BusinessException(401, "登陆已过期"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> manager.asyncSend(request)
        );

        assertEquals(401, exception.getCode());
        verify(visibleScope).resolve(List.of("lab-1"));
    }

    @Test
    void dedicatedDubboServiceDelegatesToTheInternalPath() {
        SysClientManager manager = mock(SysClientManager.class);
        MqttTaskDto request = new MqttTaskDto();
        CompletableFuture<RpcResult<MqttResponseDto>> expected =
                CompletableFuture.completedFuture(RpcResult.success(new MqttResponseDto()));
        when(manager.asyncSendFromRuleEngine(request)).thenReturn(expected);

        RuleMqttIoService service = new RuleMqttIoService(manager);

        assertSame(expected, service.asyncSend(request));
        verify(manager).asyncSendFromRuleEngine(request);
        assertEquals(
                MqttRuleIo.DUBBO_GROUP,
                RuleMqttIoService.class.getAnnotation(DubboService.class).group()
        );
    }

    private static SysClientManager manager(
            TaskHelper taskHelper,
            VisibleLaboratoryScope visibleScope
    ) {
        return new SysClientManager(
                taskHelper,
                mock(GatewayHelper.class),
                mock(ApplicationEventPublisher.class),
                new MqttOptions(),
                visibleScope
        );
    }
}
