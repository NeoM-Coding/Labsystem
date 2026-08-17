package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.eval.v2.EvalRootHandle;
import xyz.jasenon.lab.engine.eval.v2.EvalForestRegistration;
import xyz.jasenon.lab.engine.eval.v2.EvalUpdate;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一条规则在 v2 中的业务运行上下文。
 *
 * <p>设备表达式状态归全局 EvalForest 所有；Runtime 只持有 Root 句柄、
 * 时间状态、动作绑定和生命周期。</p>
 */
public final class Runtime implements AutoCloseable {

    private final String runtimeId;
    private final long generation;
    private final RuntimeLifetime lifetime;
    private final EvalForestRegistration evalForestRegistration;
    private final List<RuntimeActionGroup> actionGroups;
    private final Map<String, RuntimeActionGroup> actionGroupMap;
    private final Map<String, TimeConditionGroup> timeConditionGroups;
    private final Map<String, String> deviceGroupByRootId;
    private final Map<String, Set<String>> deviceGroupActionGroups;
    private final Map<String, Set<String>> timeGroupActionGroups;
    private final AtomicReference<RuntimeState> lifecycleState;

    public Runtime(
            String runtimeId,
            long generation,
            RuntimeLifetime lifetime,
            EvalForestRegistration evalForestRegistration,
            Map<String, TimeConditionGroup> timeConditionGroups,
            List<RuntimeActionGroup> actionGroups
    ) {
        this.runtimeId = requireText(runtimeId, "runtimeId");
        if (generation < 0) {
            throw new IllegalArgumentException("generation must not be negative");
        }
        this.generation = generation;
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.evalForestRegistration = Objects.requireNonNull(evalForestRegistration, "evalRegistration");
        if (!runtimeId.equals(evalForestRegistration.runtimeId())) {
            throw new IllegalArgumentException("Eval registration belongs to another Runtime");
        }
        this.timeConditionGroups = Map.copyOf(Objects.requireNonNull(timeConditionGroups, "timeConditionGroups"));
        this.deviceGroupByRootId = indexRootIds(evalForestRegistration);
        this.actionGroups = actionGroups == null ? List.of() : List.copyOf(actionGroups);
        this.actionGroupMap = indexActionGroups(this.actionGroups);
        this.deviceGroupActionGroups = reverseDeviceGroups(this.actionGroups);
        this.timeGroupActionGroups = reverseTimeGroups(this.actionGroups);
        validateReferences();
        this.lifecycleState = new AtomicReference<>(
                lifetime.equals(RuntimeLifetime.always()) ? RuntimeState.ACTIVE : RuntimeState.PENDING
        );
    }

    public Runtime(
            String runtimeId,
            RuntimeLifetime lifetime,
            EvalForestRegistration evalForestRegistration,
            Map<String, TimeConditionGroup> timeConditionGroups,
            List<RuntimeActionGroup> actionGroups
    ) {
        this(runtimeId, 0, lifetime, evalForestRegistration, timeConditionGroups, actionGroups);
    }

    public String runtimeId() {
        return runtimeId;
    }

    public long generation() {
        return generation;
    }

    public RuntimeLifetime lifetime() {
        return lifetime;
    }

    public List<RuntimeActionGroup> actionGroups() {
        return actionGroups;
    }

    public RuntimeActionGroup actionGroup(String actionGroupId) {
        return actionGroupMap.get(actionGroupId);
    }

    public Map<String, TimeConditionGroup> timeConditionGroups() {
        return timeConditionGroups;
    }

    public TimeConditionGroup timeConditionGroup(String groupId) {
        return timeConditionGroups.get(groupId);
    }

    public EvalRootHandle root(String deviceConditionGroupId) {
        return evalForestRegistration.root(deviceConditionGroupId);
    }

    public boolean deviceConditionSatisfied(String deviceConditionGroupId) {
        EvalRootHandle root = root(deviceConditionGroupId);
        return root != null && !root.closed() && root.value();
    }

    /** 供未来全局 Engine 建立 rootId → Runtime 的反向路由。 */
    public Set<String> rootIds() {
        return deviceGroupByRootId.keySet();
    }

    /** 将全局 Forest 的变化结果收窄为本 Runtime 需要检查的动作组。 */
    public Set<String> actionGroupIdsFor(EvalUpdate update) {
        Objects.requireNonNull(update, "update");
        Set<String> changedDeviceGroups = new HashSet<>();
        update.changedResults().keySet().forEach(rootKey -> {
            if (runtimeId.equals(rootKey.runtimeId())) {
                changedDeviceGroups.add(rootKey.conditionGroupId());
            }
        });
        return actionGroupIdsForDeviceGroups(changedDeviceGroups);
    }

    public Set<String> actionGroupIdsForDeviceGroups(Collection<String> groupIds) {
        Set<String> actionGroupIds = new HashSet<>();
        for (String groupId : groupIds) {
            actionGroupIds.addAll(deviceGroupActionGroups.getOrDefault(groupId, Set.of()));
        }
        return Set.copyOf(actionGroupIds);
    }

    public Set<String> actionGroupIdsForTimeGroup(String groupId) {
        return Set.copyOf(timeGroupActionGroups.getOrDefault(groupId, Set.of()));
    }

    public RuntimeState state() {
        return lifecycleState.get();
    }

    public boolean activate() {
        RuntimeState state = lifecycleState.get();
        return state == RuntimeState.ACTIVE
                || lifecycleState.compareAndSet(RuntimeState.PENDING, RuntimeState.ACTIVE);
    }

    public void expire() {
        lifecycleState.updateAndGet(state ->
                state == RuntimeState.CANCELLED ? state : RuntimeState.EXPIRED
        );
    }

    public boolean isActiveAt(Instant instant) {
        return lifecycleState.get() == RuntimeState.ACTIVE && lifetime.contains(instant);
    }

    public boolean isExpiredAt(Instant instant) {
        return lifecycleState.get() == RuntimeState.EXPIRED || lifetime.isExpiredAt(instant);
    }

    public boolean initializeTimeConditions(Instant instant) {
        boolean activeWindow = false;
        for (TimeConditionGroup group : timeConditionGroups.values()) {
            activeWindow |= group.initialize(instant);
        }
        return activeWindow;
    }

    @Override
    public void close() {
        lifecycleState.updateAndGet(state ->
                state == RuntimeState.EXPIRED ? state : RuntimeState.CANCELLED
        );
        evalForestRegistration.close();
    }

    private void validateReferences() {
        for (RuntimeActionGroup group : actionGroups) {
            if (root(group.deviceConditionGroupId()) == null) {
                throw new IllegalArgumentException(
                        "action group references missing device root: " + group.deviceConditionGroupId()
                );
            }
            TimeConditionGroup timeGroup = timeConditionGroups.get(group.timeConditionGroupId());
            if (timeGroup == null || timeGroup != group.timeConditionGroup()) {
                throw new IllegalArgumentException(
                        "action group references missing time group: " + group.timeConditionGroupId()
                );
            }
        }
    }

    private static Map<String, RuntimeActionGroup> indexActionGroups(List<RuntimeActionGroup> groups) {
        Map<String, RuntimeActionGroup> indexed = new LinkedHashMap<>();
        for (RuntimeActionGroup group : groups) {
            if (indexed.put(group.actionGroupId(), group) != null) {
                throw new IllegalArgumentException("duplicate action group id: " + group.actionGroupId());
            }
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, String> indexRootIds(EvalForestRegistration registration) {
        Map<String, String> indexed = new LinkedHashMap<>();
        registration.roots().forEach((groupId, root) ->
                indexed.put(root.key().externalId(), groupId));
        return Map.copyOf(indexed);
    }

    private static Map<String, Set<String>> reverseDeviceGroups(List<RuntimeActionGroup> groups) {
        return reverse(groups, RuntimeActionGroup::deviceConditionGroupId);
    }

    private static Map<String, Set<String>> reverseTimeGroups(List<RuntimeActionGroup> groups) {
        return reverse(groups, RuntimeActionGroup::timeConditionGroupId);
    }

    private static Map<String, Set<String>> reverse(
            List<RuntimeActionGroup> groups,
            java.util.function.Function<RuntimeActionGroup, String> key
    ) {
        Map<String, Set<String>> result = new ConcurrentHashMap<>();
        groups.forEach(group -> result
                .computeIfAbsent(key.apply(group), ignored -> ConcurrentHashMap.newKeySet())
                .add(group.actionGroupId()));
        return result;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
