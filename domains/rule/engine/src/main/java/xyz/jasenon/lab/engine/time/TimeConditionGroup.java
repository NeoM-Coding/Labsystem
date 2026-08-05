package xyz.jasenon.lab.engine.time;

import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeSignal;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 完整时间条件之间按 OR 关系组合；单个条件内部的日历字段按 AND 关系判断。
 */
public final class TimeConditionGroup {

    private final String groupId;
    private final Map<String, TimeCondition> conditions;
    private final Set<String> activeWindowIds = ConcurrentHashMap.newKeySet();

    public TimeConditionGroup(List<TimeCondition> conditions) {
        this("time-group-" + UUID.randomUUID(), conditions);
    }

    public TimeConditionGroup(String groupId, List<TimeCondition> conditions) {
        this.groupId = requireText(groupId, "groupId");
        Map<String, TimeCondition> byId = new LinkedHashMap<>();
        if (conditions != null) {
            for (TimeCondition condition : conditions) {
                Objects.requireNonNull(condition, "condition");
                if (byId.put(condition.conditionId(), condition) != null) {
                    throw new IllegalArgumentException(
                            "duplicate time condition id: " + condition.conditionId()
                    );
                }
            }
        }
        this.conditions = Map.copyOf(byId);
    }

    public static TimeConditionGroup always() {
        return new TimeConditionGroup(List.of());
    }

    public static TimeConditionGroup always(String groupId) {
        return new TimeConditionGroup(groupId, List.of());
    }

    public String getGroupId() {
        return groupId;
    }

    public List<TimeCondition> conditions() {
        return List.copyOf(conditions.values());
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    public boolean initialize(Instant instant) {
        // Runtime 激活或恢复时根据真实时间重建窗口状态，不能沿用旧内存值。
        activeWindowIds.clear();
        conditions.values().stream()
                .filter(TimeCondition::isWindow)
                .filter(condition -> condition.isWindowActive(instant))
                .map(TimeCondition::conditionId)
                .forEach(activeWindowIds::add);
        return !activeWindowIds.isEmpty();
    }

    public boolean apply(TimeEvent event) {
        TimeCondition condition = conditions.get(event.key().conditionId());
        if (condition == null) {
            return false;
        }
        if (event.signal() == TimeSignal.WINDOW_ENTER && condition.isWindow()) {
            return activeWindowIds.add(condition.conditionId());
        }
        if (event.signal() == TimeSignal.WINDOW_EXIT && condition.isWindow()) {
            return activeWindowIds.remove(condition.conditionId());
        }
        return event.signal() == TimeSignal.TIME_POINT && !condition.isWindow();
    }

    public boolean allows(RuntimeSignal signal) {
        if (conditions.isEmpty()) {
            return true;
        }
        if (!activeWindowIds.isEmpty()) {
            return true;
        }
        if (signal instanceof RuntimeSignal.TimePointOccurred point) {
            // TimePoint 只在本次脉冲推演中成立，不写入长期窗口状态。
            if (!groupId.equals(point.timeConditionGroupId())) {
                return false;
            }
            TimeCondition condition = conditions.get(point.event().key().conditionId());
            return condition != null && !condition.isWindow();
        }
        return false;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
