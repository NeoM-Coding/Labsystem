package xyz.jasenon.lab.engine.runtime;

import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.api.mqtt.MqttRuleIo;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.engine.action.*;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.observability.rpc.RpcClient;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 默认 Action 分发器。
 *
 * <p>ControlAction 调用 MQTT Dubbo 服务；ReportAction 当前返回结构化的
 * NOT_IMPLEMENTED，等待后续接入通知通道。</p>
 */
@Component
public class DefaultRuntimeExecutor implements RuntimeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuntimeExecutor.class);

    private final ActionExecutionTracker tracker;

    // 延迟到首次控制动作再初始化远程引用，纯规则启动不要求 MQTT provider 在线。
    @DubboReference(
            check = false,
            init = false,
            lazy = true,
            group = MqttRuleIo.DUBBO_GROUP
    )
    private MqttRuleIo mqttIo;

    @Autowired
    public DefaultRuntimeExecutor(ActionExecutionTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    DefaultRuntimeExecutor(ActionExecutionTracker tracker, MqttRuleIo mqttIo) {
        this(tracker);
        this.mqttIo = Objects.requireNonNull(mqttIo, "mqttIo");
    }

    @Override
    public CompletableFuture<ActionExecutionResult> execute(
            Runtime runtime,
            RuntimeActionGroup actionGroup,
            Action action
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(actionGroup, "actionGroup");
        Objects.requireNonNull(action, "action");

        if (action instanceof ControlAction controlAction) {
            return executeControl(runtime, actionGroup, controlAction);
        }
        if (action instanceof ReportAction reportAction) {
            return executeReport(runtime, actionGroup, reportAction);
        }
        return CompletableFuture.completedFuture(failure(
                runtime,
                actionGroup,
                action,
                null,
                new IllegalArgumentException("unsupported action type: " + action.getClass().getName())
        ));
    }

    private CompletableFuture<ActionExecutionResult> executeControl(
            Runtime runtime,
            RuntimeActionGroup actionGroup,
            ControlAction action
    ) {
        MqttTaskDto task = action.getControl();
        if (task == null) {
            return CompletableFuture.completedFuture(failure(
                    runtime,
                    actionGroup,
                    action,
                    null,
                    new IllegalArgumentException("control task must not be null")
            ));
        }

        CompletableFuture<RpcResult<MqttResponseDto>> future;
        try {
            future = Objects.requireNonNull(mqttIo, "mqttIo").asyncSend(task);
            if (future == null) {
                throw new IllegalStateException("mqttIo.asyncSend returned null");
            }
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(failure(
                    runtime,
                    actionGroup,
                    action,
                    task.getDeviceId(),
                    e
            ));
        }

        // 将正常和异常完成都转换为结构化结果，避免异常 Future 打断 Runtime mailbox。
        return future.handle((rpcResult, throwable) -> {
            if (throwable != null) {
                return failure(
                        runtime,
                        actionGroup,
                        action,
                        task.getDeviceId(),
                        unwrap(throwable)
                );
            }

            final MqttResponseDto response;
            try {
                response = RpcClient.require(rpcResult);
            } catch (RuntimeException exception) {
                return failure(
                        runtime,
                        actionGroup,
                        action,
                        task.getDeviceId(),
                        exception
                );
            }

            tracker.recordSuccess();
            String gatewayId = response == null ? null : response.getGatewayId();
            ActionExecutionResult result = ActionExecutionResult.success(
                    runtime.runtimeId(),
                    actionGroup.actionGroupId(),
                    action.is(),
                    "mqtt control completed, device-id:" + task.getDeviceId() + ", gateway-id:" + gatewayId
            );
            log.info(
                    "[RuleEngine] control action succeeded, runtime-id:{}, action-group-id:{}, "
                            + "device-id:{}, gateway-id:{}, command:{}",
                    runtime.runtimeId(),
                    actionGroup.actionGroupId(),
                    task.getDeviceId(),
                    gatewayId,
                    task.getCommandLine()
            );
            return result;
        });
    }

    private CompletableFuture<ActionExecutionResult> executeReport(
            Runtime runtime,
            RuntimeActionGroup actionGroup,
            ReportAction action
    ) {
        // 通知通道暂未实现；当前用完整日志代替实际投递，便于验证 ActionGroup 链路。
        log.info(
                "[RuleEngine] report action logged, runtime-id:{}, action-group-id:{}, "
                        + "users:{}, types:{}, content:{}",
                runtime.runtimeId(),
                actionGroup.actionGroupId(),
                action.getUserIds(),
                action.getTypes(),
                action.getContent()
        );
        return CompletableFuture.completedFuture(ActionExecutionResult.notImplemented(
                runtime.runtimeId(),
                actionGroup.actionGroupId(),
                action.is(),
                "report delivery capability is not implemented"
        ));
    }

    private ActionExecutionResult failure(
            Runtime runtime,
            RuntimeActionGroup actionGroup,
            Action action,
            String targetId,
            Throwable throwable
    ) {
        String message = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
        tracker.recordFailure(new ActionFailure(
                runtime.runtimeId(),
                actionGroup.actionGroupId(),
                action.is(),
                targetId,
                throwable.getClass().getName(),
                message,
                Instant.now()
        ));
        log.warn(
                "[RuleEngine] action execution failed, runtime-id:{}, action-group-id:{}, action-type:{}, target-id:{}",
                runtime.runtimeId(),
                actionGroup.actionGroupId(),
                action.is(),
                targetId,
                throwable
        );
        return ActionExecutionResult.failed(
                runtime.runtimeId(),
                actionGroup.actionGroupId(),
                action.is(),
                message
        );
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
