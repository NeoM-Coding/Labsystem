package xyz.jasenon.lab.engine.notification;

import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 一个动作组完成后的不可变通知快照。
 *
 * <p>它既是 WebSocket 站内信的数据源，也是后续持久化告警日志 Hook 的稳定边界。</p>
 */
public record RuleExecutionNotice(
        String eventId,
        String runtimeId,
        String actionGroupId,
        String deviceConditionGroupId,
        String timeConditionGroupId,
        Instant matchedAt,
        Instant completedAt,
        String traceId,
        List<ActionResult> actions
) {

    public RuleExecutionNotice {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public record ActionResult(
            int index,
            Action.ActionType type,
            String targetId,
            List<String> userIds,
            Set<String> reportTypes,
            String content,
            ActionExecutionResult.Status status,
            String message,
            Instant completedAt
    ) {

        public ActionResult {
            userIds = userIds == null ? List.of() : List.copyOf(userIds);
            reportTypes = reportTypes == null ? Set.of() : Set.copyOf(reportTypes);
        }
    }
}
