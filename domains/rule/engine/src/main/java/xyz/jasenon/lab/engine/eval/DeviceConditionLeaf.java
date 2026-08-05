package xyz.jasenon.lab.engine.eval;

import java.util.Objects;

/**
 * 表达式叶子及其所属的可复用设备条件组。
 */
public record DeviceConditionLeaf(
        String deviceConditionGroupId,
        EvalTreeNode node
) {

    public DeviceConditionLeaf {
        Objects.requireNonNull(deviceConditionGroupId, "deviceConditionGroupId");
        Objects.requireNonNull(node, "node");
    }
}
