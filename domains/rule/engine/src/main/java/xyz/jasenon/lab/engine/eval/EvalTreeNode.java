package xyz.jasenon.lab.engine.eval;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用平衡线段树表达严格左结合的布尔链式计算。
 *
 * <p>节点保存表达式片段对 true/false 输入的变换结果，因此既能压缩树高，
 * 又能保持 {@code A OR B AND C == (A OR B) AND C} 的链式语义。</p>
 */
@Getter
@Setter
public class EvalTreeNode {

    // 原始节点
    private EvalNode source;
    /**
     * 节点类型
     */
    private NodeType nodeType;

    /**
     * nodeType 为 logic是 选择logicType
     */
    private LogicType logicType;

    /**
     * 节点的局部结果
     */
    private volatile boolean ok;

    /**
     * 当前节点表示的表达式片段，对输入累计值 false 的输出。
     */
    private volatile boolean onFalse;

    /**
     * 当前节点表示的表达式片段，对输入累计值 true 的输出。
     */
    private volatile boolean onTrue;

    /**
     * 链式表达式的首节点没有前置累计值，语义上等价于 const(result)。
     */
    private boolean chainHead;

    // 树状结构支持
    private EvalTreeNode parent;
    private EvalTreeNode left;
    private EvalTreeNode right;

    public static EvalTreeNode leaf(EvalNode source) {
        return leaf(source, true);
    }

    private static EvalTreeNode leaf(EvalNode source, boolean chainHead) {
        EvalTreeNode node = new EvalTreeNode();
        node.source = Objects.requireNonNull(source, "source");
        node.nodeType = NodeType.LEAF;
        node.chainHead = chainHead;
        node.ok = source.isResult();
        node.refreshLeafTransform();
        return node;
    }

    public static EvalTreeNode logic(LogicType logicType, EvalTreeNode left, EvalTreeNode right) {
        Objects.requireNonNull(logicType, "logicType");
        EvalTreeNode node = segment(left, right);
        node.logicType = logicType;
        return node;
    }

    private static EvalTreeNode segment(EvalTreeNode left, EvalTreeNode right) {
        EvalTreeNode node = new EvalTreeNode();
        node.nodeType = NodeType.LOGIC;
        node.left = Objects.requireNonNull(left, "left");
        node.right = Objects.requireNonNull(right, "right");
        left.parent = node;
        right.parent = node;
        node.refreshSegmentTransform();
        return node;
    }

    public static EvalTreeNode fromChain(EvalNode head) {
        if (head == null) {
            throw new IllegalArgumentException("head must not be null");
        }

        List<EvalTreeNode> leaves = new ArrayList<>();
        leaves.add(leaf(head, true));
        EvalNode current = head.getNext();
        while (current != null) {
            leaves.add(leaf(current, false));
            current = current.getNext();
        }
        return buildBalanced(leaves, 0, leaves.size());
    }

    private static EvalTreeNode buildBalanced(List<EvalTreeNode> leaves, int start, int end) {
        if (end - start == 1) {
            return leaves.get(start);
        }

        int mid = start + (end - start) / 2;
        return segment(buildBalanced(leaves, start, mid), buildBalanced(leaves, mid, end));
    }

    public boolean refreshLeaf(String eventValue) {
        if (nodeType != NodeType.LEAF) {
            throw new IllegalStateException("only leaf node can refresh by event value");
        }
        boolean oldRootResult = root().isOk();
        boolean newResult = evaluate(eventValue);
        if (ok == newResult) {
            return false;
        }

        ok = newResult;
        source.setResult(newResult);
        refreshLeafTransform();
        if (parent == null) {
            return true;
        }
        bubble();
        return oldRootResult != root().isOk();
    }

    public EvalTreeNode root() {
        EvalTreeNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    private void bubble() {
        EvalTreeNode node = parent;
        while (node != null) {
            if (!node.refreshSegmentTransform()) {
                return;
            }
            node = node.parent;
        }
    }

    private void refreshLeafTransform() {
        if (chainHead) {
            onFalse = ok;
            onTrue = ok;
            return;
        }

        LogicType logicToPrev = source.getLogicToPrev() == null ? LogicType.AND : source.getLogicToPrev();
        logicType = logicToPrev;
        if (logicToPrev == LogicType.AND) {
            onFalse = false;
            onTrue = ok;
        } else {
            onFalse = ok;
            onTrue = true;
        }
    }

    private boolean refreshSegmentTransform() {
        boolean oldOnFalse = onFalse;
        boolean oldOnTrue = onTrue;
        boolean oldResult = ok;

        onFalse = right.apply(left.apply(false));
        onTrue = right.apply(left.apply(true));
        ok = onFalse;

        return oldOnFalse != onFalse || oldOnTrue != onTrue || oldResult != ok;
    }

    private boolean apply(boolean input) {
        return input ? onTrue : onFalse;
    }

    private boolean evaluate(String eventValue) {
        if (source == null) {
            return ok;
        }
        try {
            return TypedValueParser.compare(source.getDeviceType(), source.getField(), source.getOperator(), eventValue, source.getValue());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
