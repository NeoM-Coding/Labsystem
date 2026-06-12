package xyz.jasenon.lab.engine.eval;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalTreeNodeTests {

    @Test
    void buildsTreeByChainOrderWithoutOperatorPrecedence() {
        EvalNode dummy = dummy();
        EvalNode a = node("1", "opened", Operator.EQ, "true", LogicType.OR, false);
        EvalNode b = node("2", "roomTemperature", Operator.GT, "26", LogicType.AND, false);
        dummy.setNext(a);
        a.setNext(b);

        EvalTreeNode root = EvalTreeNode.fromChain(dummy);

        assertEquals(LogicType.AND, root.getLogicType());
        assertEquals(LogicType.OR, root.getLeft().getLogicType());
        assertFalse(root.isResult());
        assertTrue(root.getLeft().isResult());

        aLeaf(root).refreshLeaf("true");
        assertTrue(root.getLeft().isResult());
        assertFalse(root.isResult());
        root.getRight().refreshLeaf("27");
        assertTrue(root.isResult());
    }

    @Test
    void refreshesLeafAndBubblesRootChanges() {
        EvalNode dummy = dummy();
        EvalNode temperature = node("1", "roomTemperature", Operator.GT, "26", LogicType.AND, false);
        dummy.setNext(temperature);
        EvalTreeNode root = EvalTreeNode.fromChain(dummy);
        EvalTreeNode leaf = root.getRight();

        assertFalse(root.isResult());
        assertTrue(leaf.refreshLeaf("27"));
        assertTrue(root.isResult());
        assertTrue(temperature.isResult());
        assertFalse(leaf.refreshLeaf("28"));
        assertTrue(leaf.refreshLeaf("25"));
        assertFalse(root.isResult());
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

    private static EvalTreeNode aLeaf(EvalTreeNode root) {
        return root.getLeft().getRight();
    }
}
