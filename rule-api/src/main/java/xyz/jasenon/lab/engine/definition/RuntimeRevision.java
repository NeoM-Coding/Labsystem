package xyz.jasenon.lab.engine.definition;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;

import java.io.Serial;
import java.io.Serializable;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * Web 表单、Dubbo 契约与规则编译器共享的不可变规则版本。
 */
public record RuntimeRevision(
        String runtimeId,
        Boolean enabled,
        Instant activeFrom,
        Instant activeUntil,
        List<DeviceConditionGroupDefinition> deviceConditionGroups,
        List<TimeConditionGroupDefinition> timeConditionGroups,
        List<ActionGroupDefinition> actionGroups
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public RuntimeRevision {
        enabled = enabled == null ? Boolean.TRUE : enabled;
        deviceConditionGroups = immutableList(deviceConditionGroups);
        timeConditionGroups = immutableList(timeConditionGroups);
        actionGroups = immutableList(actionGroups);
    }

    public RuntimeRevision(
            String runtimeId,
            Instant activeFrom,
            Instant activeUntil,
            List<DeviceConditionGroupDefinition> deviceConditionGroups,
            List<TimeConditionGroupDefinition> timeConditionGroups,
            List<ActionGroupDefinition> actionGroups
    ) {
        this(runtimeId, Boolean.TRUE, activeFrom, activeUntil,
                deviceConditionGroups, timeConditionGroups, actionGroups);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public RuntimeRevision withEnabled(boolean value) {
        return new RuntimeRevision(runtimeId, value, activeFrom, activeUntil,
                deviceConditionGroups, timeConditionGroups, actionGroups);
    }

    public record DeviceConditionGroupDefinition(
            String groupId,
            List<DeviceConditionDefinition> conditions
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

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
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record TimeConditionGroupDefinition(
            String groupId,
            List<TimeConditionDefinition> conditions
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

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
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

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
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public ActionGroupDefinition {
            actions = immutableList(actions);
        }
    }

    public record ActionDefinition(
            ActionType type,
            MqttTaskDto control,
            List<String> userIds,
            Set<ReportType> reportTypes,
            String content
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public ActionDefinition {
            userIds = immutableList(userIds);
            reportTypes = reportTypes == null ? Set.of() : Set.copyOf(reportTypes);
        }
    }

    public enum ActionType {
        Control,
        Report
    }

    public enum ReportType {
        SMS,
        SMTP
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
