package xyz.jasenon.lab.engine.eval;

import java.util.Objects;

/**
 * 可被多个 ActionGroup 复用的设备条件组。
 *
 * <p>原始 EvalNode 链用于配置还原，EvalTreeNode 用于运行时增量求值。</p>
 */
public final class DeviceConditionGroup {

    private final String groupId;
    private final EvalNode dummyHead;
    private final EvalTreeNode root;

    public DeviceConditionGroup(String groupId, EvalNode dummyHead) {
        this(groupId, dummyHead, EvalTreeNode.fromChain(dummyHead));
    }

    public DeviceConditionGroup(
            String groupId,
            EvalNode dummyHead,
            EvalTreeNode root
    ) {
        this.groupId = requireText(groupId, "groupId");
        this.dummyHead = Objects.requireNonNull(dummyHead, "dummyHead");
        this.root = Objects.requireNonNull(root, "root");
    }

    public String getGroupId() {
        return groupId;
    }

    public EvalNode getDummyHead() {
        return dummyHead;
    }

    public EvalTreeNode getRoot() {
        return root;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
