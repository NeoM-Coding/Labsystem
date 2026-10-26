package xyz.jasenon.lab.engine.plan;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.PollAction;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.time.TimeCondition;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.time.Instant;
import java.util.*;

/**
 * Deeply immutable compiled input. No running nodes, observers or task handles.
 */
public record RuntimePlan(String runtimeId, Lifetime lifetime,
                          Map<String, List<Predicate>> conditionGroups,
                          Map<String, List<TimeCondition>> timeGroups,
                          Map<String, ActionGroup> actionGroups, List<Binding> bindings) {
    public RuntimePlan {
        if (runtimeId == null || runtimeId.isBlank()) throw new IllegalArgumentException("runtimeId required");
        Objects.requireNonNull(lifetime);
        conditionGroups = freeze(conditionGroups);
        timeGroups = freeze(timeGroups);
        actionGroups = Map.copyOf(actionGroups);
        bindings = List.copyOf(bindings);
        Set<String> ids = new HashSet<>();
        for (Binding b : bindings) {
            if (!ids.add(b.id()) || !conditionGroups.containsKey(b.conditionGroupId())
                    || !timeGroups.containsKey(b.timeGroupId()) || !actionGroups.containsKey(b.actionGroupId()))
                throw new IllegalArgumentException("invalid binding: " + b.id());
        }
    }

    private static <T> Map<String, List<T>> freeze(Map<String, List<T>> input) {
        Map<String, List<T>> copy = new LinkedHashMap<>();
        input.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        return Map.copyOf(copy);
    }

    public Set<DeviceEventKey> requiredEventKeys() {
        Set<DeviceEventKey> keys = new HashSet<>();
        conditionGroups.values().forEach(ps -> ps.forEach(p -> keys.add(p.key())));
        return Set.copyOf(keys);
    }

    public record Lifetime(Instant activeFrom, Instant activeUntil) {
        public Lifetime {
            if (activeFrom != null && activeUntil != null && !activeFrom.isBefore(activeUntil))
                throw new IllegalArgumentException("activeFrom must be before activeUntil");
        }

        public boolean pending(Instant at) {
            return activeFrom != null && at.isBefore(activeFrom);
        }

        public boolean expired(Instant at) {
            return activeUntil != null && !at.isBefore(activeUntil);
        }
    }

    public record Predicate(String id, DeviceEventKey key, Operator operator, String expected, LogicType logic) {
    }

    public record Binding(String id, String conditionGroupId, String timeGroupId, String actionGroupId,
                          TriggerMode mode) {
    }

    public record ActionGroup(String actionGroupId, List<ActionSpec> actions) {
        public ActionGroup {
            actions = List.copyOf(actions);
        }
    }

    /**
     * Mutable transport DTOs are reconstructed only at the execution boundary.
     */
    public record ActionSpec(Action.ActionType type, CommandLine command, List<Integer> args, DeviceType deviceType,
                             String deviceId, List<String> userIds, Set<ReportAction.ReportType> reportTypes,
                             String content) {
        public ActionSpec {
            args = List.copyOf(args);
            userIds = List.copyOf(userIds);
            reportTypes = Set.copyOf(reportTypes);
        }

        public Action materialize(String groupId) {
            return switch (type) {
                case Control ->
                        new ControlAction(groupId, new MqttTaskDto(command, args.stream().mapToInt(Integer::intValue).toArray(), deviceType, deviceId));
                case Report -> new ReportAction(groupId, new ArrayList<>(userIds), new HashSet<>(reportTypes), content);
                case Poll -> new PollAction(deviceType, deviceId);
            }
                    ;
        }
    }
}
