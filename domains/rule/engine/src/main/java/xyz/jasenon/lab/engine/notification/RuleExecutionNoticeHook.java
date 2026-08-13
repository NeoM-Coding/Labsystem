package xyz.jasenon.lab.engine.notification;

/**
 * 规则执行通知扩展点。
 *
 * <p>每个命中的动作组都会调用 {@link #onAlert(RuleExecutionNotice)}。持久化实现失败不会改变
 * 已经产生的动作执行结果，也不会阻止其他 Hook 或定向实时通知。</p>
 */
@FunctionalInterface
public interface RuleExecutionNoticeHook {

    void onAlert(RuleExecutionNotice notice);
}
