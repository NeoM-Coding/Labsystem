package xyz.jasenon.lab.engine.definition;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionGroupDefinition;
import xyz.jasenon.lab.engine.eval.DeviceConditionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeLifetime;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeCondition;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimePointCondition;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 把持久化 revision 编译成带索引和缓存状态的 Runtime。
 */
@Component
public class RuntimeRevisionCompiler {

    public Runtime compile(RuntimeRevision revision) {
        Objects.requireNonNull(revision, "revision");
        String runtimeId = requireText(revision.runtimeId(), "runtimeId");

        Map<String, DeviceConditionGroup> deviceGroups = index(
                revision.deviceConditionGroups(),
                DeviceConditionGroupDefinition::groupId,
                this::compileDeviceGroup,
                "device condition group"
        );
        Map<String, TimeConditionGroup> timeGroups = index(
                revision.timeConditionGroups(),
                TimeConditionGroupDefinition::groupId,
                this::compileTimeGroup,
                "time condition group"
        );

        Runtime runtime = new Runtime(
                runtimeId,
                new RuntimeLifetime(revision.activeFrom(), revision.activeUntil())
        );
        for (ActionGroupDefinition definition : revision.actionGroups()) {
            String actionGroupId = requireText(definition.actionGroupId(), "actionGroupId");
            DeviceConditionGroup deviceGroup = requireReference(
                    deviceGroups,
                    definition.deviceConditionGroupId(),
                    "deviceConditionGroupId",
                    actionGroupId
            );
            TimeConditionGroup timeGroup = requireReference(
                    timeGroups,
                    definition.timeConditionGroupId(),
                    "timeConditionGroupId",
                    actionGroupId
            );
            List<Action> actions = definition.actions().stream()
                    .map(action -> compileAction(actionGroupId, action))
                    .toList();
            runtime.registerActionGroup(new ActionGroup(
                    actionGroupId,
                    deviceGroup,
                    timeGroup,
                    actions
            ));
        }
        return runtime;
    }

    private DeviceConditionGroup compileDeviceGroup(DeviceConditionGroupDefinition definition) {
        String groupId = requireText(definition.groupId(), "deviceConditionGroup.groupId");
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);
        EvalNode tail = dummy;
        var conditionIds = new HashSet<String>();
        int index = 0;
        for (DeviceConditionDefinition condition : definition.conditions()) {
            Objects.requireNonNull(condition, "device condition");
            EvalNode node = new EvalNode();
            String conditionId = requireText(condition.conditionId(), "device condition id");
            if (!conditionIds.add(conditionId)) {
                throw new IllegalArgumentException("duplicate device condition id: " + conditionId);
            }
            node.setNodeId(conditionId);
            node.setDeviceType(Objects.requireNonNull(condition.deviceType(), "deviceType"));
            node.setDeviceId(requireText(condition.deviceId(), "deviceId"));
            node.setField(requireText(condition.field(), "field"));
            node.setOperator(Objects.requireNonNull(condition.operator(), "operator"));
            node.setValue(Objects.requireNonNull(condition.value(), "value"));
            // 第一项与 dummy 之间固定为 AND；后续项严格采用 Web 表单中的顺序关系。
            node.setLogicToPrev(index++ == 0
                    ? LogicType.AND
                    : Objects.requireNonNullElse(condition.logicToPrevious(), LogicType.AND));
            tail.setNext(node);
            tail = node;
        }
        return new DeviceConditionGroup(groupId, dummy);
    }

    private TimeConditionGroup compileTimeGroup(TimeConditionGroupDefinition definition) {
        String groupId = requireText(definition.groupId(), "timeConditionGroup.groupId");
        List<TimeCondition> conditions = new ArrayList<>();
        for (TimeConditionDefinition condition : definition.conditions()) {
            Objects.requireNonNull(condition, "time condition");
            String conditionId = requireText(condition.conditionId(), "time condition id");
            CalendarConstraint calendar = new CalendarConstraint(
                    condition.startDate(),
                    condition.endDate(),
                    condition.weekdays(),
                    ZoneId.of(requireText(condition.zoneId(), "zoneId"))
            );
            conditions.add(switch (Objects.requireNonNull(condition.type(), "time condition type")) {
                case WINDOW -> new TimeWindowCondition(
                        conditionId,
                        calendar,
                        Objects.requireNonNull(condition.startTime(), "startTime"),
                        Objects.requireNonNull(condition.endTime(), "endTime")
                );
                case TIME_POINT -> new TimePointCondition(
                        conditionId,
                        calendar,
                        Objects.requireNonNull(condition.timePoint(), "timePoint")
                );
            });
        }
        return new TimeConditionGroup(groupId, conditions);
    }

    private Action compileAction(String actionGroupId, ActionDefinition definition) {
        Objects.requireNonNull(definition, "action");
        return switch (Objects.requireNonNull(definition.type(), "action type")) {
            case Control -> new ControlAction(
                    actionGroupId,
                    Objects.requireNonNull(definition.control(), "control")
            );
            case Report -> new ReportAction(
                    actionGroupId,
                    new ArrayList<>(definition.userIds()),
                    definition.reportTypes().stream()
                            .map(type -> ReportAction.ReportType.valueOf(type.name()))
                            .collect(java.util.stream.Collectors.toSet()),
                    definition.content()
            );
        };
    }

    private static <D, V> Map<String, V> index(
            List<D> definitions,
            Function<D, String> idGetter,
            Function<D, V> compiler,
            String label
    ) {
        Map<String, V> values = new LinkedHashMap<>();
        for (D definition : definitions) {
            Objects.requireNonNull(definition, label);
            String id = requireText(idGetter.apply(definition), label + " id");
            if (values.containsKey(id)) {
                throw new IllegalArgumentException("duplicate " + label + " id: " + id);
            }
            values.put(id, compiler.apply(definition));
        }
        return values;
    }

    private static <T> T requireReference(
            Map<String, T> values,
            String referenceId,
            String field,
            String actionGroupId
    ) {
        String id = requireText(referenceId, field);
        T value = values.get(id);
        if (value == null) {
            throw new IllegalArgumentException(
                    "action group " + actionGroupId + " references missing " + field + ": " + id
            );
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
