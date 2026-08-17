package xyz.jasenon.lab.engine.eval.v2.demo;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.eval.v2.BooleanTransform;
import xyz.jasenon.lab.engine.eval.v2.CompositeNode;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.eval.v2.EvalUpdate;
import xyz.jasenon.lab.engine.eval.v2.EvalRootKey;
import xyz.jasenon.lab.engine.eval.v2.EventSourceNode;
import xyz.jasenon.lab.engine.eval.v2.LeafTransformNode;
import xyz.jasenon.lab.engine.eval.v2.ObservableValue;
import xyz.jasenon.lab.engine.eval.v2.PredicateNode;
import xyz.jasenon.lab.engine.eval.v2.RootNode;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 构造一组固定、易于观察的表达式森林，并把真实 v2 节点转换成前端拓扑。
 */
@Component
public class EvalV2DemoForest {

    private static final String DEVICE_ID = "ac-demo-01";
    private static final Map<String, String> GROUP_NAMES = Map.of(
            "safety-interlock", "安全联锁",
            "outside-comfort", "环境告警",
            "cooling-response", "制冷响应",
            "stable-zone", "稳定运行区"
    );

    private volatile EvalForest forest;
    private volatile List<DeviceEventKey> eventKeys;

    public EvalV2DemoForest() {
        rebuild();
    }

    public synchronized ForestSnapshot reset() {
        rebuild();
        return snapshot();
    }

    public EventResult accept(EventRequest request) {
        Objects.requireNonNull(request, "request");
        DeviceType deviceType = request.deviceType() == null || request.deviceType().isBlank()
                ? DeviceType.AirCondition
                : DeviceType.valueOf(request.deviceType());
        String deviceId = request.deviceId() == null || request.deviceId().isBlank()
                ? DEVICE_ID
                : request.deviceId();
        DeviceEventKey eventKey = new DeviceEventKey(deviceType, deviceId, request.field());
        EvalUpdate update = forest.accept(eventKey, request.value());
        Map<String, Boolean> changedRoots = new LinkedHashMap<>();
        update.changedResults().forEach((key, value) ->
                changedRoots.put(key.conditionGroupId(), value));
        return new EventResult(changedRoots, snapshot());
    }

    public ForestSnapshot snapshot() {
        EvalForest currentForest = forest;
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Set<GraphEdge> edges = new LinkedHashSet<>();
        Map<String, Boolean> roots = new LinkedHashMap<>();

        for (String groupId : GROUP_NAMES.keySet().stream().sorted().toList()) {
            RootNode root = currentForest.root(rootKey(groupId)).orElseThrow();
            String inputId = visitExpression(groupId, "0", root.input(), nodes, edges);
            String rootId = "root:" + groupId;
            nodes.put(rootId, new GraphNode(
                    rootId,
                    "root",
                    GROUP_NAMES.get(groupId),
                    groupId,
                    String.valueOf(root.value()),
                    groupId
            ));
            edges.add(new GraphEdge(inputId, rootId));
            roots.put(groupId, root.value());
        }

        return new ForestSnapshot(
                new Metrics(currentForest.eventSourceCount(), currentForest.predicateCount(), currentForest.treeCount()),
                List.copyOf(nodes.values()),
                List.copyOf(edges),
                Map.copyOf(roots),
                eventKeys.stream().map(DeviceEventKey::asString).toList()
        );
    }

    private void rebuild() {
        EvalForest next = new EvalForest();

        // 最右侧 errorCode == 0 为 false 时，前面三项即使变化也无法穿透顶层复合节点。
        next.register(rootKey("safety-interlock"), chain(
                condition("warm", "roomTemperature", Operator.GT, "26", null),
                condition("open", "opened", Operator.EQ, "true", LogicType.AND),
                condition("cooling", "mode", Operator.EQ, "Cooling", LogicType.AND),
                condition("no-error", "errorCode", Operator.EQ, "0", LogicType.AND)
        ));
        next.register(rootKey("outside-comfort"), chain(
                condition("cold", "roomTemperature", Operator.ST, "18", null),
                condition("hot", "roomTemperature", Operator.GT, "30", LogicType.OR),
                condition("open", "opened", Operator.EQ, "true", LogicType.AND),
                condition("has-error", "errorCode", Operator.NE, "0", LogicType.OR),
                condition("high-speed", "speed", Operator.EQ, "High", LogicType.OR)
        ));
        next.register(rootKey("cooling-response"), chain(
                condition("cooling", "mode", Operator.EQ, "Cooling", null),
                condition("high-speed", "speed", Operator.EQ, "High", LogicType.AND),
                condition("very-hot", "roomTemperature", Operator.GT, "32", LogicType.OR),
                condition("open", "opened", Operator.EQ, "true", LogicType.AND),
                condition("has-error", "errorCode", Operator.NE, "0", LogicType.OR)
        ));
        next.register(rootKey("stable-zone"), chain(
                condition("lower-bound", "roomTemperature", Operator.GE, "22", null),
                condition("upper-bound", "roomTemperature", Operator.SE, "28", LogicType.AND),
                condition("open", "opened", Operator.EQ, "true", LogicType.AND),
                condition("no-error", "errorCode", Operator.EQ, "0", LogicType.AND),
                condition("cooling", "mode", Operator.EQ, "Cooling", LogicType.AND),
                condition("not-high-speed", "speed", Operator.NE, "High", LogicType.AND)
        ));

        forest = next;
        eventKeys = List.of(
                key("roomTemperature"),
                key("opened"),
                key("mode"),
                key("errorCode"),
                key("speed")
        );
    }

    private String visitExpression(
            String groupId,
            String path,
            ObservableValue<BooleanTransform> expression,
            Map<String, GraphNode> nodes,
            Set<GraphEdge> edges
    ) {
        if (expression instanceof LeafTransformNode leaf) {
            String leafId = "leaf:" + groupId + ":" + path;
            PredicateNode predicate = leaf.predicate();
            EventSourceNode source = predicate.source();
            String sourceId = "event:" + source.eventKey().asString();
            String predicateId = sourceId + ":" + predicate.operator() + ":" + predicate.targetValue();
            nodes.putIfAbsent(sourceId, new GraphNode(
                    sourceId,
                    "event",
                    source.eventKey().field(),
                    source.eventKey().deviceId(),
                    source.value() == null ? "等待事件" : source.value(),
                    null
            ));
            nodes.putIfAbsent(predicateId, new GraphNode(
                    predicateId,
                    "predicate",
                    predicate.operator() + " " + predicate.targetValue(),
                    source.eventKey().field(),
                    String.valueOf(predicate.value()),
                    null
            ));
            nodes.put(leafId, new GraphNode(
                    leafId,
                    "leaf",
                    leaf.logicToPrevious() + " 变换",
                    predicate.operator() + " " + predicate.targetValue(),
                    format(leaf.value()),
                    groupId
            ));
            edges.add(new GraphEdge(sourceId, predicateId));
            edges.add(new GraphEdge(predicateId, leafId));
            return leafId;
        }

        CompositeNode composite = (CompositeNode) expression;
        String compositeId = "composite:" + groupId + ":" + path;
        String leftId = visitExpression(groupId, path + "L", composite.left(), nodes, edges);
        String rightId = visitExpression(groupId, path + "R", composite.right(), nodes, edges);
        nodes.put(compositeId, new GraphNode(
                compositeId,
                "composite",
                "函数复合",
                "right ∘ left",
                format(composite.value()),
                groupId
        ));
        edges.add(new GraphEdge(leftId, compositeId));
        edges.add(new GraphEdge(rightId, compositeId));
        return compositeId;
    }

    private static EvalNode chain(EvalNode... nodes) {
        if (nodes.length == 0) {
            throw new IllegalArgumentException("表达式至少需要一个节点");
        }
        for (int index = 0; index < nodes.length - 1; index++) {
            nodes[index].setNext(nodes[index + 1]);
        }
        return nodes[0];
    }

    private static EvalNode condition(
            String nodeId,
            String field,
            Operator operator,
            String target,
            LogicType logic
    ) {
        EvalNode node = new EvalNode();
        node.setNodeId(nodeId);
        node.setDeviceId(DEVICE_ID);
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(target);
        node.setLogicToPrev(logic);
        node.setResult(false);
        return node;
    }

    private static DeviceEventKey key(String field) {
        return new DeviceEventKey(DeviceType.AirCondition, DEVICE_ID, field);
    }

    private static EvalRootKey rootKey(String conditionGroupId) {
        return new EvalRootKey("eval-v2-demo", conditionGroupId);
    }

    private static String format(BooleanTransform transform) {
        return "f(0)=" + bit(transform.onFalse()) + ", f(1)=" + bit(transform.onTrue());
    }

    private static int bit(boolean value) {
        return value ? 1 : 0;
    }

    public record EventRequest(String deviceType, String deviceId, String field, String value) {
    }

    public record EventResult(Map<String, Boolean> changedRoots, ForestSnapshot forest) {
    }

    public record ForestSnapshot(
            Metrics metrics,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            Map<String, Boolean> roots,
            List<String> eventKeys
    ) {
    }

    public record Metrics(int eventSources, int predicates, int trees) {
    }

    public record GraphNode(
            String id,
            String type,
            String title,
            String subtitle,
            String value,
            String groupId
    ) {
    }

    public record GraphEdge(String source, String target) {
    }
}
