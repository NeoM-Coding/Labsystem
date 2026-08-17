package xyz.jasenon.lab.engine.eval.v2;

import xyz.jasenon.lab.engine.event.DeviceEventKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/** 一次 Root 编译产生的独占节点与共享节点引用清单。 */
final class CompiledExpressionRegistration {

    private final EvalRootKey key;
    private final String storageKey;
    private final RootNode root;
    private final List<CompositeNode> composites;
    private final List<LeafTransformNode> leaves;
    private final Map<PredicateKey, PredicateNode> referencedPredicates;
    private final Map<DeviceEventKey, EventSourceNode> referencedSources;
    private final EvalRootHandle handle;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    CompiledExpressionRegistration(
            EvalRootKey key,
            String storageKey,
            RootNode root,
            List<CompositeNode> composites,
            List<LeafTransformNode> leaves,
            Map<PredicateKey, PredicateNode> referencedPredicates,
            Map<DeviceEventKey, EventSourceNode> referencedSources,
            Consumer<CompiledExpressionRegistration> closeAction
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey");
        this.root = Objects.requireNonNull(root, "root");
        this.composites = List.copyOf(composites);
        this.leaves = List.copyOf(leaves);
        this.referencedPredicates = Map.copyOf(referencedPredicates);
        this.referencedSources = Map.copyOf(referencedSources);
        Objects.requireNonNull(closeAction, "closeAction");
        this.handle = new DefaultEvalRootHandle(key, root, () -> closeAction.accept(this));
    }

    EvalRootKey key() {
        return key;
    }

    String storageKey() {
        return storageKey;
    }

    RootNode root() {
        return root;
    }

    List<CompositeNode> composites() {
        return composites;
    }

    List<LeafTransformNode> leaves() {
        return leaves;
    }

    Map<PredicateKey, PredicateNode> referencedPredicates() {
        return referencedPredicates;
    }

    Map<DeviceEventKey, EventSourceNode> referencedSources() {
        return referencedSources;
    }

    EvalRootHandle handle() {
        return handle;
    }

    boolean dispose() {
        return disposed.compareAndSet(false, true);
    }
}
