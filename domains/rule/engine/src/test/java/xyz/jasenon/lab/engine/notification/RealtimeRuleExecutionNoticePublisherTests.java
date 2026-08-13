package xyz.jasenon.lab.engine.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.jasenon.lab.common.realtime.RealtimeAudienceType;
import xyz.jasenon.lab.common.realtime.RealtimeChannels;
import xyz.jasenon.lab.common.realtime.RealtimeMessage;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class RealtimeRuleExecutionNoticePublisherTests {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void mergesReportRecipientsAndPublishesUserAudience() throws Exception {
        RedisBus redisBus = mock(RedisBus.class);
        RealtimeRuleExecutionNoticePublisher publisher =
                new RealtimeRuleExecutionNoticePublisher(redisBus, objectMapper, List.of());

        publisher.publish(notice(List.of(
                report(0, List.of("user-1", "user-2")),
                report(1, List.of("user-2", "user-3"))
        )));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisBus).publish(eq(RealtimeChannels.EVENTS), json.capture());
        RealtimeMessage message = objectMapper.readValue(json.getValue(), RealtimeMessage.class);
        assertEquals(RealtimeAudienceType.USER, message.audienceType());
        assertEquals(List.of("user-1", "user-2", "user-3"), message.audienceIds());
        assertEquals("rule.action-group.executed", message.event().eventType());
        assertEquals("runtime", message.event().resource().type());
    }

    @Test
    void skipsRealtimePublishWhenNoReportRecipientExists() {
        RedisBus redisBus = mock(RedisBus.class);
        RealtimeRuleExecutionNoticePublisher publisher =
                new RealtimeRuleExecutionNoticePublisher(redisBus, objectMapper, List.of());

        publisher.publish(notice(List.of(control(0))));

        verify(redisBus, never()).publish(eq(RealtimeChannels.EVENTS), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void isolatesHookFailureAndStillPublishes() {
        RedisBus redisBus = mock(RedisBus.class);
        AtomicInteger invoked = new AtomicInteger();
        RuleExecutionNoticeHook failing = ignored -> { throw new IllegalStateException("storage unavailable"); };
        RuleExecutionNoticeHook healthy = ignored -> invoked.incrementAndGet();
        RealtimeRuleExecutionNoticePublisher publisher =
                new RealtimeRuleExecutionNoticePublisher(redisBus, objectMapper, List.of(failing, healthy));

        publisher.publish(notice(List.of(report(0, List.of("user-1")))));

        assertEquals(1, invoked.get());
        verify(redisBus).publish(eq(RealtimeChannels.EVENTS), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void isolatesRedisFailureAfterCallingHooks() {
        RedisBus redisBus = mock(RedisBus.class);
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisBus).publish(eq(RealtimeChannels.EVENTS), org.mockito.ArgumentMatchers.anyString());
        AtomicInteger invoked = new AtomicInteger();
        RealtimeRuleExecutionNoticePublisher publisher = new RealtimeRuleExecutionNoticePublisher(
                redisBus, objectMapper, List.of(ignored -> invoked.incrementAndGet()));

        publisher.publish(notice(List.of(report(0, List.of("user-1")))));

        assertEquals(1, invoked.get());
    }

    private RuleExecutionNotice notice(List<RuleExecutionNotice.ActionResult> actions) {
        Instant now = Instant.parse("2026-08-11T08:00:00Z");
        return new RuleExecutionNotice(
                "event-1", "runtime-1", "group-1", "device-group", "time-group",
                now, now.plusSeconds(1), "trace-1", actions
        );
    }

    private RuleExecutionNotice.ActionResult control(int index) {
        return new RuleExecutionNotice.ActionResult(
                index, Action.ActionType.Control, "device-1", List.of(), Set.of(), null,
                ActionExecutionResult.Status.SUCCESS, "completed", Instant.parse("2026-08-11T08:00:01Z")
        );
    }

    private RuleExecutionNotice.ActionResult report(int index, List<String> userIds) {
        return new RuleExecutionNotice.ActionResult(
                index, Action.ActionType.Report, null, userIds, Set.of("SMS"), "temperature alarm",
                ActionExecutionResult.Status.NOT_IMPLEMENTED, "not implemented",
                Instant.parse("2026-08-11T08:00:01Z")
        );
    }
}
