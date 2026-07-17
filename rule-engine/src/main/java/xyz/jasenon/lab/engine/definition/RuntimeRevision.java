package xyz.jasenon.lab.engine.definition;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Web 动态表单保存的不可变规则版本。
 *
 * <p>条件组只定义一次，ActionGroup 通过 ID 引用，因此一个条件组可以被多个动作组复用。
 * 该对象适合直接作为 MySQL JSON revision 的序列化边界，不携带任何运行时缓存状态。</p>
 */
public record RuntimeRevision(
        String runtimeId,
        Boolean enabled,
        Instant activeFrom,
        Instant activeUntil,
        List<DeviceConditionGroupDefinition> deviceConditionGroups,
        List<TimeConditionGroupDefinition> timeConditionGroups,
        List<ActionGroupDefinition> actionGroups
) {

    public RuntimeRevision {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        deviceConditionGroups = immutableList(deviceConditionGroups);
        timeConditionGroups = immutableList(timeConditionGroups);
        actionGroups = immutableList(actionGroups);
    }

    /**
     * 兼容尚未显式提供 enabled 的调用方和旧 JSON，默认启用。
     */
    public RuntimeRevision(
            String runtimeId,
            Instant activeFrom,
            Instant activeUntil,
            List<DeviceConditionGroupDefinition> deviceConditionGroups,
            List<TimeConditionGroupDefinition> timeConditionGroups,
            List<ActionGroupDefinition> actionGroups
    ) {
        this(
                runtimeId,
                Boolean.TRUE,
                activeFrom,
                activeUntil,
                deviceConditionGroups,
                timeConditionGroups,
                actionGroups
        );
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public RuntimeRevision withEnabled(boolean value) {
        return new RuntimeRevision(
                runtimeId,
                value,
                activeFrom,
                activeUntil,
                deviceConditionGroups,
                timeConditionGroups,
                actionGroups
        );
    }

    public record DeviceConditionGroupDefinition(
            String groupId,
            List<DeviceConditionDefinition> conditions
    ) {

        public DeviceConditionGroupDefinition {
            conditions = immutableList(conditions);
        }
    }

    public record DeviceConditionDefinition(
            String conditionId,
            DeviceType deviceType,
            String deviceId,
            String field,
            Operator operator,
            String value,
            LogicType logicToPrevious
    ) {
    }

    public record TimeConditionGroupDefinition(
            String groupId,
            List<TimeConditionDefinition> conditions
    ) {

        public TimeConditionGroupDefinition {
            conditions = immutableList(conditions);
        }
    }

    public record TimeConditionDefinition(
            String conditionId,
            TimeConditionType type,
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> weekdays,
            String zoneId,
            LocalTime startTime,
            LocalTime endTime,
            LocalTime timePoint
    ) {

        public TimeConditionDefinition {
            weekdays = weekdays == null ? Set.of() : Set.copyOf(weekdays);
        }
    }

    public enum TimeConditionType {
        WINDOW,
        TIME_POINT
    }

    public record ActionGroupDefinition(
            String actionGroupId,
            String deviceConditionGroupId,
            String timeConditionGroupId,
            List<ActionDefinition> actions
    ) {

        public ActionGroupDefinition {
            actions = immutableList(actions);
        }
    }

    /**
     * 两种动作共用一个 JSON 结构；无关字段保持 null/空集合即可。
     */
    public record ActionDefinition(
            Action.ActionType type,
            MqttTaskDto control,
            List<String> userIds,
            Set<ReportAction.ReportType> reportTypes,
            String content
    ) {

        public ActionDefinition {
            userIds = immutableList(userIds);
            reportTypes = reportTypes == null ? Set.of() : Set.copyOf(reportTypes);
        }
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
