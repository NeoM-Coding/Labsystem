package xyz.jasenon.lab.engine.action;

import lombok.Getter;
import xyz.jasenon.lab.engine.eval.DeviceConditionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.EvalTreeNode;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 一个动作组由设备条件树、时间条件组和条件满足后执行的动作列表组成。
 */
@Getter
public class ActionGroup {

    private final String actionGroupId;
    private final DeviceConditionGroup deviceConditionGroup;
    private final TimeConditionGroup timeConditionGroup;
    // 推演线程遍历动作时，规则装配线程仍可能追加动作，因此使用写时复制列表。
    private final List<Action> actions = new CopyOnWriteArrayList<>();

    public ActionGroup(String actionGroupId, EvalNode dummyHead) {
        this(
                actionGroupId,
                new DeviceConditionGroup(actionGroupId + ":device", dummyHead),
                TimeConditionGroup.always(actionGroupId + ":time:always"),
                List.of()
        );
    }

    public ActionGroup(
            String actionGroupId,
            EvalNode dummyHead,
            TimeConditionGroup timeConditionGroup
    ) {
        this(
                actionGroupId,
                new DeviceConditionGroup(actionGroupId + ":device", dummyHead),
                timeConditionGroup,
                List.of()
        );
    }

    public ActionGroup(String actionGroupId, EvalNode dummyHead, EvalTreeNode root) {
        this(
                actionGroupId,
                new DeviceConditionGroup(actionGroupId + ":device", dummyHead, root),
                TimeConditionGroup.always(actionGroupId + ":time:always"),
                List.of()
        );
    }

    public ActionGroup(String actionGroupId, EvalNode dummyHead, EvalTreeNode root, List<Action> actions) {
        this(
                actionGroupId,
                new DeviceConditionGroup(actionGroupId + ":device", dummyHead, root),
                TimeConditionGroup.always(actionGroupId + ":time:always"),
                actions
        );
    }

    public ActionGroup(
            String actionGroupId,
            EvalNode dummyHead,
            EvalTreeNode root,
            TimeConditionGroup timeConditionGroup,
            List<Action> actions
    ) {
        this(
                actionGroupId,
                new DeviceConditionGroup(actionGroupId + ":device", dummyHead, root),
                timeConditionGroup,
                actions
        );
    }

    public ActionGroup(
            String actionGroupId,
            DeviceConditionGroup deviceConditionGroup,
            TimeConditionGroup timeConditionGroup
    ) {
        this(actionGroupId, deviceConditionGroup, timeConditionGroup, List.of());
    }

    public ActionGroup(
            String actionGroupId,
            DeviceConditionGroup deviceConditionGroup,
            TimeConditionGroup timeConditionGroup,
            List<Action> actions
    ) {
        if (actionGroupId == null || actionGroupId.isBlank()) {
            throw new IllegalArgumentException("actionGroupId must not be blank");
        }
        this.actionGroupId = actionGroupId;
        this.deviceConditionGroup = Objects.requireNonNull(deviceConditionGroup, "deviceConditionGroup");
        this.timeConditionGroup = Objects.requireNonNull(timeConditionGroup, "timeConditionGroup");
        if (actions != null) {
            this.actions.addAll(actions);
        }
    }

    public String getDeviceConditionGroupId() {
        return deviceConditionGroup.getGroupId();
    }

    public String getTimeConditionGroupId() {
        return timeConditionGroup.getGroupId();
    }

    /**
     * 兼容现有调用；新代码应优先通过 DeviceConditionGroup 访问表达式树。
     */
    public EvalNode getDummyHead() {
        return deviceConditionGroup.getDummyHead();
    }

    /**
     * 兼容现有调用；新代码应优先通过 DeviceConditionGroup 访问表达式树。
     */
    public EvalTreeNode getRoot() {
        return deviceConditionGroup.getRoot();
    }

    public void addAction(Action action) {
        actions.add(Objects.requireNonNull(action, "action"));
    }
}
