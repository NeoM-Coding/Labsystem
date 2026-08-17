package xyz.jasenon.lab.engine.definition;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionGroupDefinition;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.runtime.RuntimeLifetime;
import xyz.jasenon.lab.engine.runtime.RuntimeActionGroup;
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
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.function.Function;

/**
 * 把持久化 revision 纯编译成 RuntimePlan，不修改全局推理森林。
 */
@Component
public class RuntimeRevisionCompiler {

    public RuntimePlan compile(RuntimeRevision revision) {
        Objects.requireNonNull(revision, "revision");
        String runtimeId = requireText(revision.runtimeId(), "runtimeId");

        Map<String, EvalNode> deviceChains = new LinkedHashMap<>();
        Set<String> constantTrueGroups = new LinkedHashSet<>();
        for (DeviceConditionGroupDefinition definition : revision.deviceConditionGroups()) {
            String groupId = requireText(definition.groupId(), "device condition group id");
            if (deviceChains.containsKey(groupId) || constantTrueGroups.contains(groupId)) {
                throw new IllegalArgumentException("duplicate device condition group id: " + groupId);
            }
            EvalNode chain = compileDeviceChain(definition);
            if (chain == null) {
                constantTrueGroups.add(groupId);
            } else {
                deviceChains.put(groupId, chain);
            }
        }
        Map<String, TimeConditionGroup> timeGroups = index(
                revision.timeConditionGroups(),
                TimeConditionGroupDefinition::groupId,
                this::compileTimeGroup,
                "time condition group"
        );

        List<RuntimeActionGroup> actionGroups = new ArrayList<>();
        Set<String> actionGroupIds = new LinkedHashSet<>();
        for (ActionGroupDefinition definition : revision.actionGroups()) {
            String actionGroupId = requireText(definition.actionGroupId(), "actionGroupId");
            if (!actionGroupIds.add(actionGroupId)) {
                throw new IllegalArgumentException("duplicate action group id: " + actionGroupId);
            }
            String deviceGroupId = requireText(
                    definition.deviceConditionGroupId(),
                    "deviceConditionGroupId"
            );
            if (!deviceChains.containsKey(deviceGroupId)
                    && !constantTrueGroups.contains(deviceGroupId)) {
                throw new IllegalArgumentException(
                        "action group " + actionGroupId
                                + " references missing deviceConditionGroupId: " + deviceGroupId
                );
            }
            TimeConditionGroup timeGroup = requireReference(
                    timeGroups,
                    definition.timeConditionGroupId(),
                    "timeConditionGroupId",
                    actionGroupId
            );
            List<Action> actions = definition.actions().stream()
                    .map(action -> compileAction(actionGroupId, action))
                    .toList();
            actionGroups.add(new RuntimeActionGroup(
                    actionGroupId,
                    deviceGroupId,
                    timeGroup,
                    actions
            ));
        }
        Set<DeviceEventKey> requiredEventKeys = deviceChains.values().stream()
                .flatMap(chain -> eventKeys(chain).stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new RuntimePlan(
                runtimeId,
                new RuntimeLifetime(revision.activeFrom(), revision.activeUntil()),
                deviceChains,
                constantTrueGroups,
                timeGroups,
                actionGroups,
                requiredEventKeys
        );
    }

    /** 构造不含 dummy 的真实谓词链，供 Eval v2 Forest 直接编译。 */
    EvalNode compileDeviceChain(DeviceConditionGroupDefinition definition) {
        Objects.requireNonNull(definition, "device condition group");
        EvalNode head = null;
        EvalNode tail = null;
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
            if (head == null) {
                head = node;
            } else {
                tail.setNext(node);
            }
            tail = node;
        }
        return head;
    }

    private static Set<DeviceEventKey> eventKeys(EvalNode chain) {
        Set<DeviceEventKey> keys = new LinkedHashSet<>();
        EvalNode current = chain;
        while (current != null) {
            keys.add(new DeviceEventKey(
                    current.getDeviceType(),
                    current.getDeviceId(),
                    current.getField()
            ));
            current = current.getNext();
        }
        return keys;
    }

    TimeConditionGroup compileTimeGroup(TimeConditionGroupDefinition definition) {
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

    Action compileAction(String actionGroupId, ActionDefinition definition) {
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

    static <D, V> Map<String, V> index(
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

    static <T> T requireReference(
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

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
