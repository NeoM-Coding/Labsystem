package xyz.jasenon.lab.engine.alert.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.alert.persistence.mapper.AlertLogMapper;
import xyz.jasenon.lab.engine.alert.persistence.model.AlertLogEntity;
import xyz.jasenon.lab.engine.notification.RuleExecutionNotice;
import xyz.jasenon.lab.engine.notification.RuleExecutionNoticeHook;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "lab.rule-engine.persistence", name = "enabled", havingValue = "true")
public class PersistentRuleAlertHook implements RuleExecutionNoticeHook {

    private final AlertLogMapper mapper;
    private final ObjectMapper objectMapper;

    public PersistentRuleAlertHook(AlertLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAlert(RuleExecutionNotice notice) {
        AlertLogEntity entity = new AlertLogEntity();
        entity.setEventId(notice.eventId());
        entity.setRuntimeId(notice.runtimeId());
        entity.setActionGroupId(notice.actionGroupId());
        entity.setDeviceConditionGroupId(notice.deviceConditionGroupId());
        entity.setTimeConditionGroupId(notice.timeConditionGroupId());
        entity.setMatchedAt(notice.matchedAt());
        entity.setCompletedAt(notice.completedAt());
        entity.setStatus(resolveStatus(notice));
        entity.setContent(notice.actions().stream()
                .map(RuleExecutionNotice.ActionResult::content)
                .filter(content -> content != null && !content.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("\n")));

        Set<String> userIds = new LinkedHashSet<>();
        notice.actions().forEach(action -> action.userIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .forEach(userIds::add));
        try {
            entity.setUserIds(objectMapper.writeValueAsString(userIds));
            entity.setActions(objectMapper.writeValueAsString(notice.actions()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize alert log", exception);
        }
        mapper.insert(entity);
    }

    static String resolveStatus(RuleExecutionNotice notice) {
        if (notice.actions().isEmpty()) {
            return "MATCHED";
        }
        boolean hasFailed = notice.actions().stream()
                .anyMatch(action -> action.status() == ActionExecutionResult.Status.FAILED);
        boolean hasSuccess = notice.actions().stream()
                .anyMatch(action -> action.status() == ActionExecutionResult.Status.SUCCESS);
        if (hasFailed && hasSuccess) {
            return "PARTIAL_FAILED";
        }
        if (hasFailed) {
            return "FAILED";
        }
        boolean onlyNotImplemented = notice.actions().stream()
                .allMatch(action -> action.status() == ActionExecutionResult.Status.NOT_IMPLEMENTED);
        return onlyNotImplemented ? "NOT_IMPLEMENTED" : "SUCCESS";
    }
}
