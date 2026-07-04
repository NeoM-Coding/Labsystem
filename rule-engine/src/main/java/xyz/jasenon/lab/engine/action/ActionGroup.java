package xyz.jasenon.lab.engine.action;

import lombok.Getter;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.EvalTreeNode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A condition tree and the actions invoked when its root is satisfied.
 */
@Getter
public class ActionGroup {

    private final String actionGroupId;
    private final EvalNode dummyHead;
    private final EvalTreeNode root;
    // Inference may iterate actions while rule configuration appends a new action.
    private final List<Action> actions = new CopyOnWriteArrayList<>();

    public ActionGroup(String actionGroupId, EvalNode dummyHead) {
        this(actionGroupId, dummyHead, EvalTreeNode.fromChain(dummyHead), List.of());
    }

    public ActionGroup(String actionGroupId, EvalNode dummyHead, EvalTreeNode root) {
        this(actionGroupId, dummyHead, root, List.of());
    }

    public ActionGroup(String actionGroupId, EvalNode dummyHead, EvalTreeNode root, List<Action> actions) {
        if (actionGroupId == null || actionGroupId.isBlank()) {
            throw new IllegalArgumentException("actionGroupId must not be blank");
        }
        this.actionGroupId = actionGroupId;
        this.dummyHead = Objects.requireNonNull(dummyHead, "dummyHead");
        this.root = Objects.requireNonNull(root, "root");
        if (actions != null) {
            this.actions.addAll(actions);
        }
    }

    public void addAction(Action action) {
        actions.add(Objects.requireNonNull(action, "action"));
    }
}
