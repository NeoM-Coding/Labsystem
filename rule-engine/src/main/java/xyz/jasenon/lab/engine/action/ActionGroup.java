package xyz.jasenon.lab.engine.action;

import lombok.Getter;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.EvalTreeNode;

import java.util.Objects;

@Getter
public class ActionGroup {

    private final String actionGroupId;
    private final EvalNode dummyHead;
    private final EvalTreeNode root;

    public ActionGroup(String actionGroupId, EvalNode dummyHead) {
        this(actionGroupId, dummyHead, EvalTreeNode.fromChain(dummyHead));
    }

    public ActionGroup(String actionGroupId, EvalNode dummyHead, EvalTreeNode root) {
        if (actionGroupId == null || actionGroupId.isBlank()) {
            throw new IllegalArgumentException("actionGroupId must not be blank");
        }
        this.actionGroupId = actionGroupId;
        this.dummyHead = Objects.requireNonNull(dummyHead, "dummyHead");
        this.root = Objects.requireNonNull(root, "root");
    }
}
