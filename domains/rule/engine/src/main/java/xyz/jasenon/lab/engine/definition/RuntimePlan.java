package xyz.jasenon.lab.engine.definition;

import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.runtime.RuntimeLifetime;
import xyz.jasenon.lab.engine.runtime.RuntimeActionGroup;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * RuntimeRevision 的无副作用编译结果。
 *
 * <p>计划只描述 Runtime 需要的表达式、时间条件和动作绑定，
 * 不持有 Forest 节点或观察关系。真正的拓扑安装由 Engine 统一完成。</p>
 */
public record RuntimePlan(
        String runtimeId,
        RuntimeLifetime lifetime,
        Map<String, EvalNode> deviceChains,
        Set<String> constantTrueGroups,
        Map<String, TimeConditionGroup> timeConditionGroups,
        List<RuntimeActionGroup> actionGroups,
        Set<DeviceEventKey> requiredEventKeys
) {

    public RuntimePlan {
        runtimeId = RuntimeRevisionCompiler.requireText(runtimeId, "runtimeId");
        Objects.requireNonNull(lifetime, "lifetime");
        deviceChains = Map.copyOf(Objects.requireNonNull(deviceChains, "deviceChains"));
        constantTrueGroups = Set.copyOf(Objects.requireNonNull(
                constantTrueGroups,
                "constantTrueGroups"
        ));
        timeConditionGroups = Map.copyOf(Objects.requireNonNull(
                timeConditionGroups,
                "timeConditionGroups"
        ));
        actionGroups = List.copyOf(Objects.requireNonNull(actionGroups, "actionGroups"));
        requiredEventKeys = Set.copyOf(Objects.requireNonNull(requiredEventKeys, "requiredEventKeys"));
        Set<String> deviceGroupIds = new HashSet<>(deviceChains.keySet());
        for (String constantGroup : constantTrueGroups) {
            if (!deviceGroupIds.add(constantGroup)) {
                throw new IllegalArgumentException(
                        "device condition group cannot be both chain and constant: " + constantGroup
                );
            }
        }
        Set<String> actionGroupIds = new HashSet<>();
        for (RuntimeActionGroup actionGroup : actionGroups) {
            if (!actionGroupIds.add(actionGroup.actionGroupId())) {
                throw new IllegalArgumentException(
                        "duplicate action group id: " + actionGroup.actionGroupId()
                );
            }
            if (!deviceGroupIds.contains(actionGroup.deviceConditionGroupId())) {
                throw new IllegalArgumentException(
                        "action group references missing device condition group: "
                                + actionGroup.deviceConditionGroupId()
                );
            }
            TimeConditionGroup timeGroup = timeConditionGroups.get(
                    actionGroup.timeConditionGroupId()
            );
            if (timeGroup == null || timeGroup != actionGroup.timeConditionGroup()) {
                throw new IllegalArgumentException(
                        "action group references missing time condition group: "
                                + actionGroup.timeConditionGroupId()
                );
            }
        }
    }
}
