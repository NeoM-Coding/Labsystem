package xyz.jasenon.lab.engine.compiler;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionType;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionType;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.plan.RuntimePlan.*;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import java.time.ZoneId;
import java.util.*;

@Component
public final class RuntimeRevisionCompiler {
    private static String text(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("blank identifier or value");
        return v;
    }

    private static <T> void unique(Map<String, T> map, String id, T value) {
        if (map.putIfAbsent(id, value) != null) throw new IllegalArgumentException("duplicate group: " + id);
    }

    public RuntimePlan compile(RuntimeRevision revision) {
        Objects.requireNonNull(revision, "revision");
        Map<String, List<Predicate>> devices = new LinkedHashMap<>();
        for (var group : revision.deviceConditionGroups()) {
            String id = text(group.groupId());
            Set<String> ids = new HashSet<>();
            List<Predicate> predicates = new ArrayList<>();
            for (var c : group.conditions()) {
                if (!ids.add(text(c.conditionId())))
                    throw new IllegalArgumentException("duplicate condition: " + c.conditionId());
                xyz.jasenon.lab.engine.eval.TypedValueParser.parse(c.deviceType(), c.field(), c.value());
                predicates.add(new Predicate(c.conditionId(), new DeviceEventKey(c.deviceType(), text(c.deviceId()), text(c.field())),
                        Objects.requireNonNull(c.operator()), Objects.requireNonNull(c.value()), Objects.requireNonNullElse(c.logicToPrevious(), LogicType.AND)));
            }
            unique(devices, id, predicates);
        }
        Map<String, List<TimeWindowCondition>> times = new LinkedHashMap<>();
        for (var group : revision.timeConditionGroups()) {
            List<TimeWindowCondition> windows = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (var c : group.conditions()) {
                if (c.type() != TimeConditionType.WINDOW)
                    throw new IllegalArgumentException("TIME_POINT is no longer supported in v3; replace with WINDOW or remove it: " + c.conditionId());
                if (!ids.add(text(c.conditionId())))
                    throw new IllegalArgumentException("duplicate time condition: " + c.conditionId());
                windows.add(new TimeWindowCondition(c.conditionId(), new CalendarConstraint(c.startDate(), c.endDate(), c.weekdays(), ZoneId.of(text(c.zoneId()))), Objects.requireNonNull(c.startTime()), Objects.requireNonNull(c.endTime())));
            }
            unique(times, text(group.groupId()), windows);
        }
        Map<String, ActionGroup> actions = new LinkedHashMap<>();
        List<Binding> bindings = new ArrayList<>();
        for (var g : revision.actionGroups()) {
            String id = text(g.actionGroupId()), dg = text(g.deviceConditionGroupId()), tg = text(g.timeConditionGroupId());
            if (!devices.containsKey(dg) || !times.containsKey(tg))
                throw new IllegalArgumentException("missing condition group for " + id);
            List<ActionSpec> specs = new ArrayList<>();
            for (var a : g.actions()) {
                if (Objects.requireNonNull(a.type()) == ActionType.Control) {
                    var c = Objects.requireNonNull(a.control(), "control required");
                    specs.add(new ActionSpec(Action.ActionType.Control, Objects.requireNonNull(c.getCommandLine()), c.getArgs() == null ? List.of() : Arrays.stream(c.getArgs()).boxed().toList(), Objects.requireNonNull(c.getType()), text(c.getDeviceId()), List.of(), Set.of(), null));
                } else {
                    Set<ReportAction.ReportType> types = new HashSet<>();
                    a.reportTypes().forEach(t -> types.add(ReportAction.ReportType.valueOf(t.name())));
                    specs.add(new ActionSpec(Action.ActionType.Report, null, List.of(), null, null, a.userIds(), types, a.content()));
                }
            }
            unique(actions, id, new ActionGroup(id, specs));
            bindings.add(new Binding(id, dg, tg, id, g.triggerMode()));
        }
        return new RuntimePlan(text(revision.runtimeId()), new Lifetime(revision.activeFrom(), revision.activeUntil()), devices, times, actions, bindings);
    }
}
