package xyz.jasenon.lab.engine.event;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;

import java.util.HashSet;
import java.util.Set;

/**
 * 将设备字段值应用到一个 Runtime 内所有匹配的表达式叶子。
 */
@Component
public class DeviceEventHandler {

    public RuntimeSignal handle(Runtime runtime, DeviceEvent event) {
        var leaves = runtime.leaves(event.eventKey());
        if (leaves.isEmpty()) {
            return null;
        }
        Set<String> changedGroupIds = new HashSet<>();
        for (var leaf : leaves) {
            // 叶子值变化但条件组根值未变化时，无需唤醒引用该条件组的 ActionGroup。
            if (leaf.node().refreshLeaf(event.getValue())) {
                changedGroupIds.add(leaf.deviceConditionGroupId());
            }
        }
        Set<String> candidates = runtime.actionGroupIdsForDeviceGroups(changedGroupIds);
        return candidates.isEmpty() ? null : RuntimeSignal.stateChanged(candidates);
    }
}
