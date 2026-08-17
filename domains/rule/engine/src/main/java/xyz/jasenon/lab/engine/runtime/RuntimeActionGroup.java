package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;

import java.util.List;
import java.util.Objects;

/** Runtime v2 中动作与全局设备 Root、局部时间条件之间的绑定。 */
public final class RuntimeActionGroup {

    private final String actionGroupId;
    private final String deviceConditionGroupId;
    private final TimeConditionGroup timeConditionGroup;
    private final List<Action> actions;

    public RuntimeActionGroup(
            String actionGroupId,
            String deviceConditionGroupId,
            TimeConditionGroup timeConditionGroup,
            List<Action> actions
    ) {
        this.actionGroupId = requireText(actionGroupId, "actionGroupId");
        this.deviceConditionGroupId = requireText(deviceConditionGroupId, "deviceConditionGroupId");
        this.timeConditionGroup = Objects.requireNonNull(timeConditionGroup, "timeConditionGroup");
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public String actionGroupId() {
        return actionGroupId;
    }

    public String deviceConditionGroupId() {
        return deviceConditionGroupId;
    }

    public String timeConditionGroupId() {
        return timeConditionGroup.getGroupId();
    }

    public TimeConditionGroup timeConditionGroup() {
        return timeConditionGroup;
    }

    public List<Action> actions() {
        return actions;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
