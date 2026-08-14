package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 由字段索引驱动的特化观察者网络森林。
 *
 * <p>不同事件源可以并发传播。每个节点对外发布自己最新可见的值，
 * 并且只保证自身状态变更的有序性，使整个网络持续贴合现实状态，
 * 而不是基于某个冻结快照进行推理。</p>
 */
public final class EvalForest {

    private final Map<DeviceEventKey, EventSourceNode> eventSources = new ConcurrentHashMap<>();
    private final Map<PredicateKey, PredicateNode> predicates = new ConcurrentHashMap<>();
    private final Map<String, RootNode> roots = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<String, RootDelta>> activeBatch = new ThreadLocal<>();

    public synchronized RootNode register(String groupId, EvalNode chain) {
        requireGroupId(groupId);
        Objects.requireNonNull(chain, "chain");
        if (roots.containsKey(groupId)) {
            throw new IllegalArgumentException("duplicate groupId: " + groupId);
        }

        List<LeafTransformNode> leaves = compileLeaves(chain);
        ObservableValue<BooleanTransform> expression = buildBalanced(leaves, 0, leaves.size());
        RootNode root = new RootNode(groupId, expression, this::recordRootChange);
        roots.put(groupId, root);
        return root;
    }

    public EvalUpdate accept(DeviceEventKey eventKey, String eventValue) {
        Objects.requireNonNull(eventKey, "eventKey");
        EventSourceNode source = eventSources.get(eventKey);
        if (source == null) {
            return new EvalUpdate(eventKey, Map.of());
        }

        Map<String, RootDelta> batch = new LinkedHashMap<>();
        activeBatch.set(batch);
        try {
            source.accept(eventValue);
            Map<String, Boolean> changed = new LinkedHashMap<>();
            batch.forEach((groupId, delta) -> {
                RootNode root = roots.get(groupId);
                boolean latest = root == null ? delta.current() : root.value();
                if (delta.initial() != latest) {
                    changed.put(groupId, latest);
                }
            });
            return new EvalUpdate(eventKey, changed);
        } finally {
            activeBatch.remove();
        }
    }

    public Optional<Boolean> rootResult(String groupId) {
        RootNode root = roots.get(groupId);
        return root == null ? Optional.empty() : Optional.of(root.value());
    }

    public Optional<RootNode> root(String groupId) {
        return Optional.ofNullable(roots.get(groupId));
    }

    public Optional<EventSourceNode> eventSource(DeviceEventKey eventKey) {
        return Optional.ofNullable(eventSources.get(eventKey));
    }

    public int treeCount() {
        return roots.size();
    }

    public int eventSourceCount() {
        return eventSources.size();
    }

    public int predicateCount() {
        return predicates.size();
    }

    private List<LeafTransformNode> compileLeaves(EvalNode chain) {
        // 先完整校验再注册共享节点，避免非法链在森林中留下半编译的观察关系。
        EvalNode validating = chain;
        while (validating != null) {
            if (!isPredicate(validating)) {
                throw new IllegalArgumentException("v2 expression must start with and contain only predicate nodes");
            }
            validating = validating.getNext();
        }

        List<LeafTransformNode> leaves = new ArrayList<>();
        EvalNode current = chain;
        boolean firstExpression = true;
        while (current != null) {
            DeviceEventKey eventKey = new DeviceEventKey(
                    current.getDeviceType(), current.getDeviceId(), current.getField()
            );
            EventSourceNode source = eventSources.computeIfAbsent(eventKey, EventSourceNode::new);
            PredicateKey predicateKey = new PredicateKey(eventKey, current.getOperator(), current.getValue());
            EvalNode predicateSource = current;
            PredicateNode predicate = predicates.computeIfAbsent(predicateKey, ignored ->
                    new PredicateNode(
                            source,
                            predicateSource.getOperator(),
                            predicateSource.getValue(),
                            predicateSource.isResult()
                    )
            );
            // 首项没有前置累计值，在编译边界统一归一为 AND；
            // Root 从 true 单位元开始应用函数，因此不再需要 dummy 链首节点。
            leaves.add(LeafTransformNode.predicate(predicate, firstExpression
                    ? LogicType.AND
                    : current.getLogicToPrev()));
            firstExpression = false;
            current = current.getNext();
        }
        return leaves;
    }

    private ObservableValue<BooleanTransform> buildBalanced(
            List<LeafTransformNode> leaves,
            int start,
            int end
    ) {
        if (end - start == 1) {
            return leaves.get(start);
        }
        int middle = start + (end - start) / 2;
        return new CompositeNode(
                buildBalanced(leaves, start, middle),
                buildBalanced(leaves, middle, end)
        );
    }

    private void recordRootChange(String groupId, boolean previous, boolean current) {
        Map<String, RootDelta> batch = activeBatch.get();
        if (batch == null) {
            return;
        }
        batch.compute(groupId, (ignored, existing) -> existing == null
                ? new RootDelta(previous, current)
                : new RootDelta(existing.initial(), current));
    }

    private static boolean isPredicate(EvalNode node) {
        return node.getDeviceType() != null
                && node.getDeviceId() != null
                && node.getField() != null
                && node.getOperator() != null
                && node.getValue() != null;
    }

    private static void requireGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            throw new IllegalArgumentException("groupId must not be blank");
        }
    }

    private record PredicateKey(
            DeviceEventKey eventKey,
            xyz.jasenon.lab.engine.eval.Operator operator,
            String targetValue
    ) {
    }

    private record RootDelta(boolean initial, boolean current) {
    }
}
