package xyz.jasenon.lab.engine.runtime;

import lombok.AccessLevel;
import lombok.Getter;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.DeviceConditionGroup;
import xyz.jasenon.lab.engine.eval.DeviceConditionLeaf;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.EvalTreeNode;
import xyz.jasenon.lab.engine.eval.NodeType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.event.EventKey;
import xyz.jasenon.lab.engine.event.EventTable;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

/**
 * 一条用户规则在内存中的运行上下文。
 *
 * <p>同时保存生命周期、ActionGroup，以及设备 EventKey 到表达式叶子的反向索引。</p>
 */
@Getter
public class Runtime {

    private final String runtimeId;
    private final RuntimeLifetime lifetime;
    private final List<ActionGroup> actionGroups = new CopyOnWriteArrayList<>();
    private final Map<String, ActionGroup> actionGroupMap = new ConcurrentHashMap<>();
    private final Map<String, DeviceConditionGroup> deviceConditionGroups = new ConcurrentHashMap<>();
    private final Map<String, TimeConditionGroup> timeConditionGroups = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> deviceGroupActionGroups = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> timeGroupActionGroups = new ConcurrentHashMap<>();
    private final EventTable<Set<DeviceConditionLeaf>> roots = new EventTable<>();
    private final Map<String, EvalTreeNode> treeRootMap = new ConcurrentHashMap<>();
    private final Map<String, EvalNode> dummyNodeMap = new ConcurrentHashMap<>();
    @Getter(AccessLevel.NONE)
    private final AtomicReference<RuntimeState> lifecycleState;

    public Runtime(String runtimeId) {
        this(runtimeId, RuntimeLifetime.always(), List.of());
    }

    public Runtime(String runtimeId, RuntimeLifetime lifetime) {
        this(runtimeId, lifetime, List.of());
    }

    public Runtime(String runtimeId, List<ActionGroup> actionGroups) {
        this(runtimeId, RuntimeLifetime.always(), actionGroups);
    }

    public Runtime(
            String runtimeId,
            RuntimeLifetime lifetime,
            List<ActionGroup> actionGroups
    ) {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        this.runtimeId = runtimeId;
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.lifecycleState = new AtomicReference<>(
                lifetime.equals(RuntimeLifetime.always()) ? RuntimeState.ACTIVE : RuntimeState.PENDING
        );
        if (actionGroups != null) {
            actionGroups.forEach(this::registerActionGroup);
        }
    }

    public synchronized void registerActionGroup(ActionGroup actionGroup) {
        Objects.requireNonNull(actionGroup, "actionGroup");
        if (actionGroupMap.containsKey(actionGroup.getActionGroupId())) {
            throw new IllegalArgumentException(
                    "duplicate action group id: " + actionGroup.getActionGroupId()
            );
        }
        validateConditionGroupReference(
                deviceConditionGroups,
                actionGroup.getDeviceConditionGroupId(),
                actionGroup.getDeviceConditionGroup(),
                "device"
        );
        validateConditionGroupReference(
                timeConditionGroups,
                actionGroup.getTimeConditionGroupId(),
                actionGroup.getTimeConditionGroup(),
                "time"
        );

        registerDeviceConditionGroup(actionGroup.getDeviceConditionGroup());
        registerTimeConditionGroup(actionGroup.getTimeConditionGroup());
        actionGroupMap.put(actionGroup.getActionGroupId(), actionGroup);
        actionGroups.add(actionGroup);
        deviceGroupActionGroups
                .computeIfAbsent(actionGroup.getDeviceConditionGroupId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(actionGroup.getActionGroupId());
        timeGroupActionGroups
                .computeIfAbsent(actionGroup.getTimeConditionGroupId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(actionGroup.getActionGroupId());
    }

    public Set<DeviceConditionLeaf> leaves(EventKey key) {
        return roots.getOrDefault(key, Set.of());
    }

    public ActionGroup actionGroup(String actionGroupId) {
        return actionGroupMap.get(actionGroupId);
    }

    public TimeConditionGroup timeConditionGroup(String timeConditionGroupId) {
        return timeConditionGroups.get(timeConditionGroupId);
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

    public RuntimeState getState() {
        return lifecycleState.get();
    }

    public boolean activate() {
        // ACTIVE 重复回调视为成功；终态 Runtime 不允许重新激活。
        RuntimeState state = lifecycleState.get();
        return state == RuntimeState.ACTIVE
                || lifecycleState.compareAndSet(RuntimeState.PENDING, RuntimeState.ACTIVE);
    }

    public void expire() {
        lifecycleState.updateAndGet(state ->
                state == RuntimeState.CANCELLED ? state : RuntimeState.EXPIRED
        );
    }

    public void cancel() {
        lifecycleState.set(RuntimeState.CANCELLED);
    }

    public boolean isActiveAt(Instant instant) {
        return lifecycleState.get() == RuntimeState.ACTIVE && lifetime.contains(instant);
    }

    public boolean isExpiredAt(Instant instant) {
        return lifecycleState.get() == RuntimeState.EXPIRED || lifetime.isExpiredAt(instant);
    }

    public boolean initializeTimeConditions(Instant instant) {
        // Runtime 激活时统一按当前时间恢复全部 ActionGroup 的窗口状态。
        boolean activeWindow = false;
        for (TimeConditionGroup timeConditionGroup : timeConditionGroups.values()) {
            activeWindow |= timeConditionGroup.initialize(instant);
        }
        return activeWindow;
    }

    private void registerDeviceConditionGroup(DeviceConditionGroup group) {
        DeviceConditionGroup existing = deviceConditionGroups.putIfAbsent(group.getGroupId(), group);
        if (existing != null && existing != group) {
            throw new IllegalArgumentException(
                    "device condition group id points to different instances: " + group.getGroupId()
            );
        }
        if (existing == null) {
            treeRootMap.put(group.getGroupId(), group.getRoot());
            dummyNodeMap.put(group.getGroupId(), group.getDummyHead());
            indexLeaves(group.getGroupId(), group.getRoot());
        }
    }

    private void registerTimeConditionGroup(TimeConditionGroup group) {
        TimeConditionGroup existing = timeConditionGroups.putIfAbsent(group.getGroupId(), group);
        if (existing != null && existing != group) {
            throw new IllegalArgumentException(
                    "time condition group id points to different instances: " + group.getGroupId()
            );
        }
    }

    private static <T> void validateConditionGroupReference(
            Map<String, T> groups,
            String groupId,
            T group,
            String type
    ) {
        T existing = groups.get(groupId);
        if (existing != null && existing != group) {
            throw new IllegalArgumentException(
                    type + " condition group id points to different instances: " + groupId
            );
        }
    }

    private void indexLeaves(String deviceConditionGroupId, EvalTreeNode node) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == NodeType.LEAF) {
            EvalNode source = node.getSource();
            if (source != null
                    && source.getDeviceType() != null
                    && source.getDeviceId() != null
                    && source.getField() != null
                    && source.getOperator() != null) {
                DeviceEventKey key = new DeviceEventKey(source.getDeviceType(), source.getDeviceId(), source.getField());
                roots.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
                        .add(new DeviceConditionLeaf(deviceConditionGroupId, node));
            }
            return;
        }
        indexLeaves(deviceConditionGroupId, node.getLeft());
        indexLeaves(deviceConditionGroupId, node.getRight());
    }
}
