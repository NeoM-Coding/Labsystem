package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.action.ActionGroup;

import java.util.concurrent.CompletableFuture;

/**
 * 执行满足条件的 ActionGroup 中的一条 Action。
 *
 * <p>返回的 Future 定义动作的完整生命周期；本轮全部 Future 完成前，
 * RuntimeScheduler 不会释放该 Runtime 的单飞状态。</p>
 */
@FunctionalInterface
public interface RuntimeExecutor {

    /**
     * @return 描述动作最终结果的非 null Future
     */
    CompletableFuture<ActionExecutionResult> execute(Runtime runtime, ActionGroup actionGroup, Action action);
}
