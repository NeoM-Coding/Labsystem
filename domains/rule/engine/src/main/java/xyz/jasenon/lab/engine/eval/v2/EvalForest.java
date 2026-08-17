package xyz.jasenon.lab.engine.eval.v2;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 由字段索引驱动的特化观察者网络森林。
 *
 * <p>不同事件源可以并发传播。每个节点对外发布自己最新可见的值，
 * 并且只保证自身状态变更的有序性，使整个网络持续贴合现实状态，
 * 而不是基于某个冻结快照进行推理。</p>
 */
@Component
public final class EvalForest {

    private final Map<DeviceEventKey, EventSourceNode> eventSources = new ConcurrentHashMap<>();
    private final Map<PredicateKey, PredicateNode> predicates = new ConcurrentHashMap<>();
    private final Map<String, RootNode> roots = new ConcurrentHashMap<>();
    private final Map<String, CompiledExpressionRegistration> registrations = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<EvalRootKey, RootDelta>> activeBatch = new ThreadLocal<>();
    private final ReentrantReadWriteLock topologyLock = new ReentrantReadWriteLock();

    public EvalRootHandle register(EvalRootKey rootKey, EvalNode chain) {
        Objects.requireNonNull(rootKey, "rootKey");
        CompiledExpressionRegistration registration = registerInternal(
                rootKey,
                rootKey.externalId(),
                chain
        );
        return registration.handle();
    }

    public boolean unregister(EvalRootKey rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        CompiledExpressionRegistration registration = registrations.get(rootKey.externalId());
        if (registration == null) {
            return false;
        }
        registration.handle().close();
        return true;
    }

    /**
     * 原子注册一个 Runtime 的全部设备条件组；任一链编译失败时回滚本批已建根。
     */
    public EvalForestRegistration registerRuntime(
            String runtimeId,
            Map<String, EvalNode> chains
    ) {
        return registerRuntime(runtimeId, chains, Set.of());
    }

    /**
     * 原子替换一个 Runtime 的全部表达式根。
     *
     * <p>构造新版期间旧网络仍保持观察关系，因此共享事件源的当前值不会丢失；
     * 只有新版完整注册成功后才释放旧网络。失败时恢复旧注册表。</p>
     */
    public EvalForestRegistration replaceRuntime(
            String runtimeId,
            Map<String, EvalNode> chains,
            Set<String> constantTrueGroups
    ) {
        requireGroupId(runtimeId);
        topologyLock.writeLock().lock();
        try {
            List<CompiledExpressionRegistration> previous = registrations.values().stream()
                    .filter(registration -> runtimeId.equals(registration.key().runtimeId()))
                    .toList();
            previous.forEach(registration -> {
                registrations.remove(registration.storageKey(), registration);
                roots.remove(registration.storageKey(), registration.root());
            });

            final EvalForestRegistration replacement;
            try {
                replacement = registerRuntime(runtimeId, chains, constantTrueGroups);
            } catch (RuntimeException exception) {
                previous.forEach(registration -> {
                    roots.put(registration.storageKey(), registration.root());
                    registrations.put(registration.storageKey(), registration);
                });
                throw exception;
            }
            previous.forEach(this::disposeRegistration);
            return replacement;
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    public EvalForestRegistration registerRuntime(
            String runtimeId,
            Map<String, EvalNode> chains,
            Set<String> constantTrueGroups
    ) {
        requireGroupId(runtimeId);
        Objects.requireNonNull(chains, "chains");
        Objects.requireNonNull(constantTrueGroups, "constantTrueGroups");
        topologyLock.writeLock().lock();
        try {
            Map<String, EvalRootHandle> handles = new LinkedHashMap<>();
            try {
                chains.forEach((conditionGroupId, chain) -> {
                    requireGroupId(conditionGroupId);
                    EvalRootHandle handle = register(
                            new EvalRootKey(runtimeId, conditionGroupId),
                            Objects.requireNonNull(chain, "chain")
                    );
                    handles.put(conditionGroupId, handle);
                });
                constantTrueGroups.forEach(conditionGroupId -> {
                    requireGroupId(conditionGroupId);
                    if (handles.containsKey(conditionGroupId)) {
                        throw new IllegalArgumentException(
                                "condition group cannot be both a chain and a constant: " + conditionGroupId
                        );
                    }
                    CompiledExpressionRegistration registration = registerConstantInternal(
                            new EvalRootKey(runtimeId, conditionGroupId),
                            true
                    );
                    handles.put(conditionGroupId, registration.handle());
                });
            } catch (RuntimeException exception) {
                handles.values().forEach(EvalRootHandle::close);
                throw exception;
            }
            Map<String, EvalRootHandle> immutableHandles = Map.copyOf(handles);
            return new EvalForestRegistration(
                    runtimeId,
                    immutableHandles,
                    () -> closeRuntimeRegistration(immutableHandles)
            );
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private CompiledExpressionRegistration registerConstantInternal(EvalRootKey rootKey, boolean value) {
        String storageKey = rootKey.externalId();
        topologyLock.writeLock().lock();
        try {
            if (roots.containsKey(storageKey)) {
                throw new IllegalArgumentException("duplicate root: " + rootKey);
            }
            FixedTransformValue expression = new FixedTransformValue(BooleanTransform.constant(value));
            RootNode root = new RootNode(storageKey, expression, this::recordRootChange);
            CompiledExpressionRegistration registration = new CompiledExpressionRegistration(
                    rootKey,
                    storageKey,
                    root,
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of(),
                    this::unregister
            );
            roots.put(storageKey, root);
            registrations.put(storageKey, registration);
            return registration;
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private CompiledExpressionRegistration registerInternal(
            EvalRootKey rootKey,
            String storageKey,
            EvalNode chain
    ) {
        Objects.requireNonNull(chain, "chain");
        topologyLock.writeLock().lock();
        try {
            if (roots.containsKey(storageKey)) {
                throw new IllegalArgumentException("duplicate root: " + rootKey);
            }

            Map<PredicateKey, PredicateNode> referencedPredicates = new LinkedHashMap<>();
            Map<DeviceEventKey, EventSourceNode> referencedSources = new LinkedHashMap<>();
            List<LeafTransformNode> leaves = compileLeaves(
                    chain,
                    referencedPredicates,
                    referencedSources
            );
            List<CompositeNode> composites = new ArrayList<>();
            ObservableValue<BooleanTransform> expression = buildBalanced(
                    leaves,
                    0,
                    leaves.size(),
                    composites
            );
            RootNode root = new RootNode(storageKey, expression, this::recordRootChange);
            CompiledExpressionRegistration registration = new CompiledExpressionRegistration(
                    rootKey,
                    storageKey,
                    root,
                    List.copyOf(composites),
                    List.copyOf(leaves),
                    Map.copyOf(referencedPredicates),
                    Map.copyOf(referencedSources),
                    this::unregister
            );
            roots.put(storageKey, root);
            registrations.put(storageKey, registration);
            return registration;
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    public EvalUpdate accept(DeviceEventKey eventKey, String eventValue) {
        Objects.requireNonNull(eventKey, "eventKey");
        topologyLock.readLock().lock();
        try {
            EventSourceNode source = eventSources.get(eventKey);
            if (source == null) {
                return new EvalUpdate(eventKey, Map.of());
            }

            Map<EvalRootKey, RootDelta> batch = new LinkedHashMap<>();
            activeBatch.set(batch);
            try {
                source.accept(eventValue);
                Map<EvalRootKey, Boolean> changed = new LinkedHashMap<>();
                batch.forEach((rootKey, delta) -> {
                    RootNode root = roots.get(rootKey.externalId());
                    boolean latest = root == null ? delta.current() : root.value();
                    if (delta.initial() != latest) {
                        changed.put(rootKey, latest);
                    }
                });
                return new EvalUpdate(eventKey, changed);
            } finally {
                activeBatch.remove();
            }
        } finally {
            topologyLock.readLock().unlock();
        }
    }

    public Optional<Boolean> rootResult(EvalRootKey rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        RootNode root = roots.get(rootKey.externalId());
        return root == null ? Optional.empty() : Optional.of(root.value());
    }

    public Optional<RootNode> root(EvalRootKey rootKey) {
        Objects.requireNonNull(rootKey, "rootKey");
        return Optional.ofNullable(roots.get(rootKey.externalId()));
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

    private List<LeafTransformNode> compileLeaves(
            EvalNode chain,
            Map<PredicateKey, PredicateNode> referencedPredicates,
            Map<DeviceEventKey, EventSourceNode> referencedSources
    ) {
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
            referencedSources.put(eventKey, source);
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
            referencedPredicates.put(predicateKey, predicate);
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
            int end,
            List<CompositeNode> composites
    ) {
        if (end - start == 1) {
            return leaves.get(start);
        }
        int middle = start + (end - start) / 2;
        CompositeNode composite = new CompositeNode(
                buildBalanced(leaves, start, middle, composites),
                buildBalanced(leaves, middle, end, composites)
        );
        composites.add(composite);
        return composite;
    }

    private void unregister(CompiledExpressionRegistration registration) {
        topologyLock.writeLock().lock();
        try {
            if (!registrations.remove(registration.storageKey(), registration)) {
                return;
            }
            roots.remove(registration.storageKey(), registration.root());
            disposeRegistration(registration);
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private void disposeRegistration(CompiledExpressionRegistration registration) {
        if (!registration.dispose()) {
            return;
        }
        registration.root().close();
        registration.composites().forEach(CompositeNode::close);
        registration.leaves().forEach(LeafTransformNode::close);

        registration.referencedPredicates().forEach((key, predicate) -> {
            if (predicate.leafObserverCount() == 0 && predicates.remove(key, predicate)) {
                predicate.close();
            }
        });
        registration.referencedSources().forEach((key, source) -> {
            if (source.predicateObserverCount() == 0) {
                eventSources.remove(key, source);
            }
        });
    }

    private void closeRuntimeRegistration(Map<String, EvalRootHandle> handles) {
        topologyLock.writeLock().lock();
        try {
            handles.values().forEach(EvalRootHandle::close);
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private void recordRootChange(String groupId, boolean previous, boolean current) {
        Map<EvalRootKey, RootDelta> batch = activeBatch.get();
        if (batch == null) {
            return;
        }
        CompiledExpressionRegistration registration = registrations.get(groupId);
        if (registration == null) {
            return;
        }
        batch.compute(registration.key(), (ignored, existing) -> existing == null
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

    private record RootDelta(boolean initial, boolean current) {
    }

    /** 无设备条件时使用的恒定函数输入，不在森林中伪造事件叶子。 */
    private static final class FixedTransformValue implements ObservableValue<BooleanTransform> {

        private final BooleanTransform value;
        private final ObservableSupport<BooleanTransform> observable = new ObservableSupport<>();

        private FixedTransformValue(BooleanTransform value) {
            this.value = value;
        }

        @Override
        public BooleanTransform value() {
            return value;
        }

        @Override
        public Observation observe(ValueObserver<BooleanTransform> observer) {
            return observable.add(observer);
        }
    }

}
