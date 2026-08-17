package xyz.jasenon.lab.engine.benchmark;

import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.eval.v2.EvalRootKey;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.List;

/** 为 JMH 构造可重复的共享森林，不经过 Spring 或持久化链路。 */
final class EvalV2ForestFixture {

    static final List<String> FIELDS = List.of(
            "roomTemperature", "opened", "mode", "errorCode", "speed"
    );

    private EvalV2ForestFixture() {
    }

    static Scenario create(int deviceCount) {
        EvalForest forest = new EvalForest();
        List<List<DeviceEventKey>> keysByDevice = new ArrayList<>(deviceCount);
        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            String deviceId = "ac-" + deviceIndex;
            registerProductionLikeTrees(forest, deviceId, deviceIndex);
            List<DeviceEventKey> keys = FIELDS.stream()
                    .map(field -> key(deviceId, field))
                    .toList();
            keysByDevice.add(keys);
            forest.accept(keys.get(0), "20");
            forest.accept(keys.get(1), "false");
            forest.accept(keys.get(2), "Heating");
            forest.accept(keys.get(3), "0");
            forest.accept(keys.get(4), "Low");
        }
        return new Scenario(forest, List.copyOf(keysByDevice));
    }

    static HighFanOutScenario createHighFanOut(
            int deviceCount,
            int treesPerDevice,
            int leavesPerTree
    ) {
        EvalForest forest = new EvalForest();
        List<DeviceEventKey> keys = new ArrayList<>(deviceCount);
        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            String deviceId = "fanout-ac-" + deviceIndex;
            DeviceEventKey eventKey = key(deviceId, "errorCode");
            keys.add(eventKey);
            for (int treeIndex = 0; treeIndex < treesPerDevice; treeIndex++) {
                EvalNode head = null;
                EvalNode tail = null;
                for (int leafIndex = 0; leafIndex < leavesPerTree; leafIndex++) {
                    EvalNode leaf = condition(
                            "leaf-" + treeIndex + '-' + leafIndex,
                            deviceId,
                            "errorCode",
                            Operator.GT,
                            Integer.toString((treeIndex + leafIndex) & 7),
                            leafIndex == 0 || (leafIndex & 1) == 0 ? LogicType.AND : LogicType.OR
                    );
                    if (head == null) {
                        head = leaf;
                    } else {
                        tail.setNext(leaf);
                    }
                    tail = leaf;
                }
                forest.register(
                        new EvalRootKey("fanout-runtime-" + deviceIndex, "tree-" + treeIndex),
                        head
                );
            }
            forest.accept(eventKey, "0");
        }
        return new HighFanOutScenario(forest, List.copyOf(keys));
    }

    private static void registerProductionLikeTrees(
            EvalForest forest,
            String deviceId,
            int deviceIndex
    ) {
        String runtimeId = "benchmark-runtime-" + deviceIndex;
        forest.register(new EvalRootKey(runtimeId, "warm"), chain(
                condition("warm", deviceId, "roomTemperature", Operator.GT, "26", LogicType.AND),
                condition("open", deviceId, "opened", Operator.EQ, "true", LogicType.AND),
                condition("cooling", deviceId, "mode", Operator.EQ, "Cooling", LogicType.OR),
                condition("no-error", deviceId, "errorCode", Operator.EQ, "0", LogicType.AND)
        ));
        forest.register(new EvalRootKey(runtimeId, "risk"), chain(
                condition("hot", deviceId, "roomTemperature", Operator.GT, "30", LogicType.AND),
                condition("error", deviceId, "errorCode", Operator.NE, "0", LogicType.OR),
                condition("fast", deviceId, "speed", Operator.EQ, "High", LogicType.OR)
        ));
        forest.register(new EvalRootKey(runtimeId, "comfort"), chain(
                condition("lower", deviceId, "roomTemperature", Operator.GT, "22", LogicType.AND),
                condition("upper", deviceId, "roomTemperature", Operator.ST, "28", LogicType.AND),
                condition("open", deviceId, "opened", Operator.EQ, "true", LogicType.AND)
        ));
        forest.register(new EvalRootKey(runtimeId, "cooling"), chain(
                condition("mode", deviceId, "mode", Operator.EQ, "Cooling", LogicType.AND),
                condition("open", deviceId, "opened", Operator.EQ, "true", LogicType.AND)
        ));
        forest.register(new EvalRootKey(runtimeId, "compound"), chain(
                condition("warm", deviceId, "roomTemperature", Operator.GT, "26", LogicType.AND),
                condition("open", deviceId, "opened", Operator.EQ, "true", LogicType.OR),
                condition("safe", deviceId, "errorCode", Operator.EQ, "0", LogicType.AND),
                condition("cooling", deviceId, "mode", Operator.EQ, "Cooling", LogicType.OR),
                condition("fast", deviceId, "speed", Operator.EQ, "High", LogicType.AND)
        ));
    }

    private static EvalNode chain(EvalNode... nodes) {
        for (int index = 0; index + 1 < nodes.length; index++) {
            nodes[index].setNext(nodes[index + 1]);
        }
        return nodes[0];
    }

    private static EvalNode condition(
            String nodeId,
            String deviceId,
            String field,
            Operator operator,
            String value,
            LogicType logic
    ) {
        EvalNode node = new EvalNode();
        node.setNodeId(nodeId);
        node.setDeviceType(DeviceType.AirCondition);
        node.setDeviceId(deviceId);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setLogicToPrev(logic);
        return node;
    }

    private static DeviceEventKey key(String deviceId, String field) {
        return new DeviceEventKey(DeviceType.AirCondition, deviceId, field);
    }

    record Scenario(EvalForest forest, List<List<DeviceEventKey>> keysByDevice) {
    }

    record HighFanOutScenario(EvalForest forest, List<DeviceEventKey> keysByDevice) {
    }
}
