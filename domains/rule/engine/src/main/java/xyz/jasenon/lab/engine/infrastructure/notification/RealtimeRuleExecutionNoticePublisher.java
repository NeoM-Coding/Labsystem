package xyz.jasenon.lab.engine.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.realtime.*;
import xyz.jasenon.lab.engine.execution.notice.RuleExecutionNotice;
import xyz.jasenon.lab.engine.execution.notice.RuleExecutionNoticeHook;
import xyz.jasenon.lab.engine.execution.notice.RuleExecutionNoticePublisher;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.util.*;

@Component
public class RealtimeRuleExecutionNoticePublisher implements RuleExecutionNoticePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimeRuleExecutionNoticePublisher.class);

    private final RedisBus redisBus;
    private final ObjectMapper objectMapper;
    private final List<RuleExecutionNoticeHook> hooks;

    public RealtimeRuleExecutionNoticePublisher(
            @Nullable RedisBus redisBus,
            ObjectMapper objectMapper,
            List<RuleExecutionNoticeHook> hooks
    ) {
        this.redisBus = redisBus;
        this.objectMapper = objectMapper;
        this.hooks = List.copyOf(hooks);
    }

    @Override
    public void publish(RuleExecutionNotice notice) {
        for (RuleExecutionNoticeHook hook : hooks) {
            try {
                hook.onAlert(notice);
            } catch (RuntimeException exception) {
                log.warn("[RuleEngine] execution notice hook failed, runtime-id:{}, action-group-id:{}",
                        notice.runtimeId(), notice.actionGroupId(), exception);
            }
        }

        if (redisBus == null) {
            return;
        }

        Set<String> userIds = new LinkedHashSet<>();
        notice.actions().forEach(action -> action.userIds().stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .forEach(userIds::add));
        if (userIds.isEmpty()) {
            return;
        }

        Map<String, Object> data = objectMapper.convertValue(
                notice,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );
        RealtimeEvent event = new RealtimeEvent(
                RealtimeEvent.CURRENT_VERSION,
                notice.eventId(),
                RealtimeEventTypes.RULE_ACTION_GROUP_EXECUTED,
                notice.completedAt(),
                "rule-engine",
                notice.traceId(),
                new RealtimeResource("runtime", notice.runtimeId(), null),
                data
        );
        RealtimeMessage message = new RealtimeMessage(RealtimeAudienceType.USER, List.copyOf(userIds), event);
        try {
            redisBus.publish(RealtimeChannels.EVENTS, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("[RuleEngine] publish execution notice failed, runtime-id:{}, action-group-id:{}",
                    notice.runtimeId(), notice.actionGroupId(), exception);
        }
    }
}
