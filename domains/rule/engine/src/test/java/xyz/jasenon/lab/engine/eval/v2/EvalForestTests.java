package xyz.jasenon.lab.engine.eval.v2;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalForestTests {

    private static final DeviceEventKey TEMPERATURE =
            new DeviceEventKey(DeviceType.AirCondition, "ac-1", "roomTemperature");

    @Test
    void composesAllUnaryBooleanFunctions() {
        List<BooleanTransform> functions = List.of(
                BooleanTransform.constant(false),
                BooleanTransform.constant(true),
                new BooleanTransform(false, true),
                new BooleanTransform(true, false)
        );

        for (BooleanTransform left : functions) {
            for (BooleanTransform right : functions) {
                BooleanTransform composed = left.then(right);
                assertEquals(right.apply(left.apply(false)), composed.apply(false));
                assertEquals(right.apply(left.apply(true)), composed.apply(true));
            }
        }
    }

    @Test
    void sharesEventSourcesAndEqualPredicatesAcrossTrees() {
        EvalForest forest = new EvalForest();
        forest.register("warm", chain(node("1", Operator.GT, "26", LogicType.AND, false)));
        forest.register("hot", chain(node("2", Operator.GT, "30", LogicType.AND, false)));
        forest.register("warm-copy", chain(node("3", Operator.GT, "26", LogicType.OR, false)));

        assertEquals(3, forest.treeCount());
        assertEquals(1, forest.eventSourceCount());
        assertEquals(2, forest.predicateCount());
        assertEquals(2, forest.eventSource(TEMPERATURE).orElseThrow().predicateObserverCount());

        LeafTransformNode warmLeaf = firstLeaf(forest.root("warm").orElseThrow().input());
        LeafTransformNode copyLeaf = firstLeaf(forest.root("warm-copy").orElseThrow().input());
        assertSame(warmLeaf.predicate(), copyLeaf.predicate());
        assertNotSame(warmLeaf, copyLeaf);
    }

    @Test
    void keepsStrictLeftAssociativeMeaningInBalancedNetwork() {
        EvalNode a = node("a", Operator.GT, "10", null, false);
        EvalNode b = node("b", Operator.GT, "20", LogicType.OR, false);
        EvalNode c = node("c", Operator.GT, "30", LogicType.AND, false);
        a.setNext(b);
        b.setNext(c);

        EvalForest forest = new EvalForest();
        RootNode root = forest.register("expression", a);
        assertInstanceOf(CompositeNode.class, root.input());

        assertFalse(root.value());
        assertFalse(forest.accept(TEMPERATURE, "15").changed());
        assertFalse(root.value()); // 严格左结合：(true OR false) AND false
        assertTrue(forest.accept(TEMPERATURE, "35").changed());
        assertTrue(root.value());
    }

    @Test
    void normalizesTheFirstPredicateWithoutAChainHeadLeafMode() {
        EvalForest forest = new EvalForest();
        EvalNode first = node("first", Operator.GT, "26", LogicType.OR, false);

        RootNode root = forest.register("single", first);
        LeafTransformNode leaf = (LeafTransformNode) root.input();

        assertEquals(LogicType.AND, leaf.logicToPrevious());
        assertTrue(forest.accept(TEMPERATURE, "28").changed());
        assertTrue(root.value());
    }

    @Test
    void rejectsDummyNodesAtTheV2Boundary() {
        EvalForest forest = new EvalForest();
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);
        dummy.setNext(node("first", Operator.GT, "26", LogicType.AND, false));

        assertThrows(IllegalArgumentException.class, () -> forest.register("legacy-chain", dummy));
    }

    @Test
    void coalescesSeveralAffectedLeavesIntoOneFinalRootChange() {
        EvalNode first = node("a", Operator.GT, "10", null, false);
        EvalNode second = node("b", Operator.GT, "20", LogicType.AND, false);
        first.setNext(second);

        EvalForest forest = new EvalForest();
        forest.register("both", first);

        EvalUpdate update = forest.accept(TEMPERATURE, "25");
        assertEquals(java.util.Map.of("both", true), update.changedResults());
        assertEquals(true, forest.rootResult("both").orElseThrow());

        assertFalse(forest.accept(TEMPERATURE, "26").changed());
        assertTrue(forest.accept(TEMPERATURE, "15").changed());
        assertFalse(forest.rootResult("both").orElseThrow());
    }

    @Test
    void suppressesTransientRootChangesWithinOneEventBatch() {
        EvalNode first = node("a", Operator.GT, "20", null, true);
        EvalNode second = node("b", Operator.ST, "20", LogicType.OR, false);
        first.setNext(second);

        EvalForest forest = new EvalForest();
        forest.register("either-side", first);

        EvalUpdate update = forest.accept(TEMPERATURE, "15");

        assertFalse(update.changed());
        assertTrue(forest.rootResult("either-side").orElseThrow());
    }

    @Test
    void buildsABalancedCompositeNetwork() {
        EvalNode head = node("0", Operator.GT, "0", null, false);
        EvalNode tail = head;
        for (int index = 1; index < 16; index++) {
            EvalNode next = node(String.valueOf(index), Operator.GT, String.valueOf(index), LogicType.AND, false);
            tail.setNext(next);
            tail = next;
        }

        EvalForest forest = new EvalForest();
        RootNode root = forest.register("balanced", head);

        assertTrue(height(root.input()) <= 5);
    }

    @Test
    void ignoresUnknownKeysAndTreatsInvalidValuesAsFalse() {
        EvalForest forest = new EvalForest();
        forest.register("warm", chain(node("1", Operator.GT, "26", LogicType.AND, true)));

        DeviceEventKey unknown = new DeviceEventKey(DeviceType.AirCondition, "ac-2", "roomTemperature");
        assertFalse(forest.accept(unknown, "30").changed());
        assertTrue(forest.accept(TEMPERATURE, "not-a-number").changed());
        assertFalse(forest.rootResult("warm").orElseThrow());
    }

    @Test
    void predicatesRegisteredLaterInheritTheCurrentEventValue() {
        EvalForest forest = new EvalForest();
        forest.register("warm", chain(node("1", Operator.GT, "26", LogicType.AND, false)));
        forest.accept(TEMPERATURE, "30");

        forest.register("hot", chain(node("2", Operator.GT, "28", LogicType.AND, false)));

        assertTrue(forest.rootResult("hot").orElseThrow());
    }

    @Test
    void supportsConcurrentInferenceAndKeepsTheLatestRootRealityVisible() throws Exception {
        EvalForest forest = new EvalForest();
        forest.register("warm", chain(node("1", Operator.GT, "26", LogicType.AND, false)));
        List<Boolean> observations = new CopyOnWriteArrayList<>();
        forest.root("warm").orElseThrow().observe((source, previous, current) -> observations.add(current));

        int eventCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < eventCount; index++) {
            String value = index % 2 == 0 ? "20" : "30";
            futures.add(executor.submit(() -> {
                start.await();
                forest.accept(TEMPERATURE, value);
                return null;
            }));
        }
        start.countDown();
        for (var future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        forest.accept(TEMPERATURE, "30");
        assertTrue(forest.rootResult("warm").orElseThrow());
        forest.accept(TEMPERATURE, "20");
        assertFalse(forest.rootResult("warm").orElseThrow());
        assertEquals(false, observations.get(observations.size() - 1));
    }

    private static EvalNode chain(EvalNode condition) {
        return condition;
    }

    private static EvalNode node(
            String id,
            Operator operator,
            String target,
            LogicType logic,
            boolean result
    ) {
        EvalNode node = new EvalNode();
        node.setNodeId(id);
        node.setDeviceId("ac-1");
        node.setDeviceType(DeviceType.AirCondition);
        node.setField("roomTemperature");
        node.setOperator(operator);
        node.setValue(target);
        node.setLogicToPrev(logic);
        node.setResult(result);
        return node;
    }

    private static LeafTransformNode firstLeaf(ObservableValue<BooleanTransform> node) {
        if (node instanceof LeafTransformNode leaf) {
            return leaf;
        }
        return firstLeaf(((CompositeNode) node).right());
    }

    private static int height(ObservableValue<BooleanTransform> node) {
        if (node instanceof LeafTransformNode) {
            return 1;
        }
        CompositeNode composite = (CompositeNode) node;
        return 1 + Math.max(height(composite.left()), height(composite.right()));
    }
}
