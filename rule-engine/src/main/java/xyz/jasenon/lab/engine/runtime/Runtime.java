package xyz.jasenon.lab.engine.runtime;

import lombok.Getter;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.EvalTreeNode;
import xyz.jasenon.lab.engine.eval.NodeType;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.event.EventKey;
import xyz.jasenon.lab.engine.event.EventTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class Runtime {

    private final String runtimeId;
    private final List<ActionGroup> actionGroups = new ArrayList<>();
    private final EventTable<Set<EvalTreeNode>> roots = new EventTable<>();
    private final Map<String, EvalTreeNode> treeRootMap = new ConcurrentHashMap<>();
    private final Map<String, EvalNode> dummyNodeMap = new ConcurrentHashMap<>();

    public Runtime(String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
        this.runtimeId = runtimeId;
    }

    public Runtime(String runtimeId, List<ActionGroup> actionGroups) {
        this(runtimeId);
        if (actionGroups != null) {
            actionGroups.forEach(this::registerActionGroup);
        }
    }

    public void registerActionGroup(ActionGroup actionGroup) {
        Objects.requireNonNull(actionGroup, "actionGroup");
        actionGroups.add(actionGroup);
        treeRootMap.put(actionGroup.getActionGroupId(), actionGroup.getRoot());
        dummyNodeMap.put(actionGroup.getActionGroupId(), actionGroup.getDummyHead());
        indexLeaves(actionGroup.getRoot());
    }

    public Set<EvalTreeNode> leaves(EventKey key) {
        return roots.getOrDefault(key, Set.of());
    }

    private void indexLeaves(EvalTreeNode node) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == NodeType.LEAF) {
            EvalNode source = node.getSource();
            if (source != null
                    && source.getDeviceType() != null
                    && source.getDeviceId() != null
                    && source.getField() != null
                    && source.getOperator() != null) {
                DeviceEventKey key = new DeviceEventKey(source.getDeviceType(), source.getDeviceId(), source.getField());
                roots.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(node);
            }
            return;
        }
        indexLeaves(node.getLeft());
        indexLeaves(node.getRight());
    }
}
