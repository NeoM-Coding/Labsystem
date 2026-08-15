package xyz.jasenon.lab.engine.benchmark;

import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.List;

/** 构造与 EvalV2DemoForest 相同的四棵树，供基准测试稳定复用。 */
final class EvalV2ForestFixture {

    static final List<String> FIELDS = List.of(
            "roomTemperature", "opened", "mode", "errorCode", "speed"
    );

    private EvalV2ForestFixture() {
    }

    static Scenario create(int deviceCount) {
        EvalForest forest = new EvalForest();
        List<List<DeviceEventKey>> keysByDevice = new ArrayList<>(deviceCount);
        for (int index = 0; index < deviceCount; index++) {
            String deviceId = "ac-benchmark-" + index;
            registerDevice(forest, deviceId, index);
            keysByDevice.add(FIELDS.stream()
                    .map(field -> key(deviceId, field))
                    .toList());
        }
        verifyTopology(forest, deviceCount);
        return new Scenario(forest, List.copyOf(keysByDevice));
    }

    /**
     * 构造高扇出森林：每棵树的所有叶子都监听同一个温度字段，但使用不同阈值。
     * 一次温度反转会让该设备的全部谓词、叶子和组合节点发生真实传播。
     */
    static HighFanOutScenario createHighFanOut(int deviceCount, int treesPerDevice, int leavesPerTree) {
        EvalForest forest = new EvalForest();
        List<DeviceEventKey> keys = new ArrayList<>(deviceCount);
        for (int deviceIndex = 0; deviceIndex < deviceCount; deviceIndex++) {
            String deviceId = "ac-high-fanout-" + deviceIndex;
            DeviceEventKey temperature = key(deviceId, "roomTemperature");
            keys.add(temperature);
            for (int treeIndex = 0; treeIndex < treesPerDevice; treeIndex++) {
                EvalNode[] nodes = new EvalNode[leavesPerTree];
                for (int leafIndex = 0; leafIndex < leavesPerTree; leafIndex++) {
                    int threshold = treeIndex * leavesPerTree + leafIndex + 1;
                    nodes[leafIndex] = condition(
                            deviceId,
                            "threshold-" + threshold,
                            "roomTemperature",
                            Operator.GT,
                            Integer.toString(threshold),
                            leafIndex == 0 ? null : LogicType.AND
                    );
                }
                forest.register(
                        "device-" + deviceIndex + ":large-tree-" + treeIndex,
                        chain(nodes)
                );
            }
        }

        int expectedTrees = deviceCount * treesPerDevice;
        int expectedPredicates = expectedTrees * leavesPerTree;
        if (forest.eventSourceCount() != deviceCount
                || forest.treeCount() != expectedTrees
                || forest.predicateCount() != expectedPredicates) {
            throw new IllegalStateException("high-fan-out benchmark topology is incomplete");
        }
        return new HighFanOutScenario(forest, List.copyOf(keys));
    }

    private static void verifyTopology(EvalForest forest, int deviceCount) {
        if (forest.eventSourceCount() != deviceCount * 5
                || forest.predicateCount() != deviceCount * 12
                || forest.treeCount() != deviceCount * 4) {
            throw new IllegalStateException("benchmark forest topology does not match EvalV2DemoForest");
        }
    }

    private static void registerDevice(EvalForest forest, String deviceId, int index) {
        String prefix = "device-" + index + ":";
        forest.register(prefix + "safety-interlock", chain(
                condition(deviceId, "warm", "roomTemperature", Operator.GT, "26", null),
                condition(deviceId, "open", "opened", Operator.EQ, "true", LogicType.AND),
                condition(deviceId, "cooling", "mode", Operator.EQ, "Cooling", LogicType.AND),
                condition(deviceId, "no-error", "errorCode", Operator.EQ, "0", LogicType.AND)
        ));
        forest.register(prefix + "outside-comfort", chain(
                condition(deviceId, "cold", "roomTemperature", Operator.ST, "18", null),
                condition(deviceId, "hot", "roomTemperature", Operator.GT, "30", LogicType.OR),
                condition(deviceId, "open", "opened", Operator.EQ, "true", LogicType.AND),
                condition(deviceId, "has-error", "errorCode", Operator.NE, "0", LogicType.OR),
                condition(deviceId, "high-speed", "speed", Operator.EQ, "High", LogicType.OR)
        ));
        forest.register(prefix + "cooling-response", chain(
                condition(deviceId, "cooling", "mode", Operator.EQ, "Cooling", null),
                condition(deviceId, "high-speed", "speed", Operator.EQ, "High", LogicType.AND),
                condition(deviceId, "very-hot", "roomTemperature", Operator.GT, "32", LogicType.OR),
                condition(deviceId, "open", "opened", Operator.EQ, "true", LogicType.AND),
                condition(deviceId, "has-error", "errorCode", Operator.NE, "0", LogicType.OR)
        ));
        forest.register(prefix + "stable-zone", chain(
                condition(deviceId, "lower-bound", "roomTemperature", Operator.GE, "22", null),
                condition(deviceId, "upper-bound", "roomTemperature", Operator.SE, "28", LogicType.AND),
                condition(deviceId, "open", "opened", Operator.EQ, "true", LogicType.AND),
                condition(deviceId, "no-error", "errorCode", Operator.EQ, "0", LogicType.AND),
                condition(deviceId, "cooling", "mode", Operator.EQ, "Cooling", LogicType.AND),
                condition(deviceId, "not-high-speed", "speed", Operator.NE, "High", LogicType.AND)
        ));
    }

    private static EvalNode chain(EvalNode... nodes) {
        for (int index = 0; index < nodes.length - 1; index++) {
            nodes[index].setNext(nodes[index + 1]);
        }
        return nodes[0];
    }

    private static EvalNode condition(
            String deviceId,
            String nodeId,
            String field,
            Operator operator,
            String target,
            LogicType logic
    ) {
        EvalNode node = new EvalNode();
        node.setNodeId(nodeId);
        node.setDeviceId(deviceId);
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(target);
        node.setLogicToPrev(logic);
        node.setResult(false);
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
