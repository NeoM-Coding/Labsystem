package xyz.jasenon.lab.engine.eval.v2;

import java.util.Objects;

/** 全局森林中一棵表达式树的稳定身份。 */
public record EvalRootKey(String runtimeId, String conditionGroupId) {

    public EvalRootKey {
        runtimeId = requireText(runtimeId, "runtimeId");
        conditionGroupId = requireText(conditionGroupId, "conditionGroupId");
    }

    /** 使用长度前缀避免不同 Runtime 与条件组拼接后发生歧义。 */
    public String externalId() {
        return runtimeId.length() + ":" + runtimeId
                + ":" + conditionGroupId.length() + ":" + conditionGroupId;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
