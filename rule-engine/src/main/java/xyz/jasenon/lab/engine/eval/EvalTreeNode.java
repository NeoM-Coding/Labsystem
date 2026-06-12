package xyz.jasenon.lab.engine.eval;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/**
 * BST快速迭代 根结果
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
    private volatile boolean result;

    // 树状结构支持
    private EvalTreeNode parent;
    private EvalTreeNode left;
    private EvalTreeNode right;

    public static EvalTreeNode leaf(EvalNode source) {
        EvalTreeNode node = new EvalTreeNode();
        node.source = Objects.requireNonNull(source, "source");
        node.nodeType = NodeType.LEAF;
        node.result = source.isResult();
        return node;
    }

    public static EvalTreeNode logic(LogicType logicType, EvalTreeNode left, EvalTreeNode right) {
        EvalTreeNode node = new EvalTreeNode();
        node.nodeType = NodeType.LOGIC;
        node.logicType = Objects.requireNonNull(logicType, "logicType");
        node.left = Objects.requireNonNull(left, "left");
        node.right = Objects.requireNonNull(right, "right");
        left.parent = node;
        right.parent = node;
        node.result = node.calculate();
        return node;
    }

    public static EvalTreeNode fromChain(EvalNode head) {
        if (head == null) {
            throw new IllegalArgumentException("head must not be null");
        }

        EvalTreeNode root = leaf(head);
        EvalNode current = head.getNext();
        while (current != null) {
            EvalTreeNode nextLeaf = leaf(current);
            LogicType logicToPrev = current.getLogicToPrev() == null ? LogicType.AND : current.getLogicToPrev();
            root = logic(logicToPrev, root, nextLeaf);
            current = current.getNext();
        }
        return root;
    }

    public boolean refreshLeaf(String eventValue) {
        if (nodeType != NodeType.LEAF) {
            throw new IllegalStateException("only leaf node can refresh by event value");
        }
        boolean oldRootResult = root().isResult();
        boolean newResult = evaluate(eventValue);
        result = newResult;
        source.setResult(newResult);
        bubble();
        return oldRootResult != root().isResult();
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
            node.result = node.calculate();
            node = node.parent;
        }
    }

    private boolean calculate() {
        if (nodeType == NodeType.LEAF) {
            return result;
        }
        if (logicType == LogicType.AND) {
            return left.isResult() && right.isResult();
        }
        return left.isResult() || right.isResult();
    }

    private boolean evaluate(String eventValue) {
        if (source == null) {
            return result;
        }
        try {
            return TypedValueParser.compare(source.getDeviceType(), source.getField(), source.getOperator(), eventValue, source.getValue());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
