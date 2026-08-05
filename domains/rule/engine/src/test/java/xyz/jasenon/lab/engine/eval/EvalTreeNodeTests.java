package xyz.jasenon.lab.engine.eval;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalTreeNodeTests {

    @Test
    void buildsTreeByChainOrderWithoutOperatorPrecedence() {
        EvalNode a = node("1", "opened", Operator.EQ, "true", null, true);
        EvalNode b = node("2", "opened", Operator.EQ, "true", LogicType.OR, false);
        EvalNode c = node("3", "roomTemperature", Operator.GT, "26", LogicType.AND, false);
        a.setNext(b);
        b.setNext(c);

        EvalTreeNode root = EvalTreeNode.fromChain(a);

        // Strict chain order: A || B && C is evaluated as (A || B) && C.
        assertFalse(root.isOk());

        findLeaf(root, "3").refreshLeaf("27");
        assertTrue(root.isOk());
    }

    @Test
    void refreshesLeafAndBubblesRootChanges() {
        EvalNode dummy = dummy();
        EvalNode temperature = node("1", "roomTemperature", Operator.GT, "26", LogicType.AND, false);
        dummy.setNext(temperature);
        EvalTreeNode root = EvalTreeNode.fromChain(dummy);
        EvalTreeNode leaf = root.getRight();

        assertFalse(root.isOk());
        assertTrue(leaf.refreshLeaf("27"));
        assertTrue(root.isOk());
        assertTrue(temperature.isResult());
        assertFalse(leaf.refreshLeaf("28"));
        assertTrue(leaf.refreshLeaf("25"));
        assertFalse(root.isOk());
    }

    @Test
    void refreshStopsWhenParentResultDoesNotChange() {
        EvalNode dummy = dummy();
        EvalNode opened = node("1", "opened", Operator.EQ, "true", LogicType.OR, false);
        dummy.setNext(opened);
        EvalTreeNode root = EvalTreeNode.fromChain(dummy);
        EvalTreeNode leaf = root.getRight();

        assertTrue(root.isOk());
        assertFalse(leaf.isOk());

        assertFalse(leaf.refreshLeaf("true"));

        assertTrue(leaf.isOk());
        assertTrue(opened.isResult());
        assertTrue(root.isOk());
    }

    @Test
    void fromChainBuildsBalancedSegmentTree() {
        EvalNode head = dummy();
        EvalNode tail = head;
        for (int i = 1; i <= 16; i++) {
            EvalNode next = node(String.valueOf(i), "roomTemperature", Operator.GT, "26", LogicType.AND, true);
            tail.setNext(next);
            tail = next;
        }

        EvalTreeNode root = EvalTreeNode.fromChain(head);

        assertEquals(17, countLeaves(root));
        assertTrue(height(root) <= 6);
    }

    private static EvalNode dummy() {
        EvalNode node = new EvalNode();
        node.setResult(true);
        return node;
    }

    private static EvalNode node(String id, String field, Operator operator, String value, LogicType logicToPrev, boolean result) {
        EvalNode node = new EvalNode();
        node.setNodeId(id);
        node.setDeviceId("ac-1");
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setLogicToPrev(logicToPrev);
        node.setResult(result);
        return node;
    }

    private static EvalTreeNode findLeaf(EvalTreeNode node, String nodeId) {
        if (node == null) {
            throw new IllegalArgumentException("leaf not found: " + nodeId);
        }
        if (node.getNodeType() == NodeType.LEAF) {
            EvalNode source = node.getSource();
            if (source != null && nodeId.equals(source.getNodeId())) {
                return node;
            }
            throw new IllegalArgumentException("leaf not found: " + nodeId);
        }

        try {
            return findLeaf(node.getLeft(), nodeId);
        } catch (IllegalArgumentException ignored) {
            return findLeaf(node.getRight(), nodeId);
        }
    }

    private static int height(EvalTreeNode node) {
        if (node == null) {
            return 0;
        }
        return 1 + Math.max(height(node.getLeft()), height(node.getRight()));
    }

    private static int countLeaves(EvalTreeNode node) {
        if (node == null) {
            return 0;
        }
        if (node.getNodeType() == NodeType.LEAF) {
            return 1;
        }
        return countLeaves(node.getLeft()) + countLeaves(node.getRight());
    }
}
