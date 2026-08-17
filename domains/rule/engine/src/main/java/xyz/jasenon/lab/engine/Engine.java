package xyz.jasenon.lab.engine;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.definition.RuntimePlan;
import xyz.jasenon.lab.engine.eval.v2.EvalForest;
import xyz.jasenon.lab.engine.eval.v2.EvalForestRegistration;
import xyz.jasenon.lab.engine.eval.v2.EvalRootKey;
import xyz.jasenon.lab.engine.eval.v2.EvalUpdate;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.EngineEvent;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.runtime.RuntimeLifecycleManager;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignalRouter;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;
import xyz.jasenon.lab.engine.time.TimeScheduleService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 全局 EvalForest 驱动的规则引擎入口。
 *
 * <p>Engine 只管理 Runtime 拓扑与事件路由。设备事件只进入 Forest 一次，
 * Root 变化再按 Runtime 身份收窄为动作组调度信号。</p>
 */
@Component
public class Engine {

    private final EvalForest evalForest;
    private final RuntimeScheduler runtimeScheduler;
    private final RuntimeLifecycleManager lifecycleManager;
    private final TimeScheduleService timeScheduleService;
    private final RuntimeSignalRouter signalRouter;
    private final ConcurrentHashMap<String, Runtime> runtimes = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();
    private final ReentrantReadWriteLock topologyLock = new ReentrantReadWriteLock();

    public Engine(
            EvalForest evalForest,
            RuntimeScheduler runtimeScheduler,
            RuntimeLifecycleManager lifecycleManager,
            TimeScheduleService timeScheduleService
    ) {
        this.evalForest = Objects.requireNonNull(evalForest, "evalForest");
        this.runtimeScheduler = Objects.requireNonNull(runtimeScheduler, "runtimeScheduler");
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
        this.timeScheduleService = Objects.requireNonNull(timeScheduleService, "timeScheduleService");
        this.signalRouter = new RuntimeSignalRouter();
    }

    /** 原子安装或替换一个 Runtime。 */
    public Runtime register(RuntimePlan plan) {
        Objects.requireNonNull(plan, "plan");
        topologyLock.writeLock().lock();
        try {
            String runtimeId = plan.runtimeId();
            Runtime previous = runtimes.get(runtimeId);
            EvalForestRegistration registration = evalForest.replaceRuntime(
                    runtimeId,
                    plan.deviceChains(),
                    plan.constantTrueGroups()
            );
            Runtime replacement;
            try {
                replacement = new Runtime(
                        runtimeId,
                        generations.incrementAndGet(),
                        plan.lifetime(),
                        registration,
                        plan.timeConditionGroups(),
                        plan.actionGroups()
                );
            } catch (RuntimeException exception) {
                registration.close();
                throw exception;
            }

            lifecycleManager.cancel(runtimeId);
            timeScheduleService.cancel(runtimeId);
            runtimeScheduler.cancel(runtimeId);
            runtimes.put(runtimeId, replacement);
            if (previous != null) {
                previous.close();
            }
            lifecycleManager.track(
                    runtimeId,
                    replacement.lifetime(),
                    () -> activate(replacement),
                    () -> expire(replacement)
            );
            return replacement;
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    public void remove(String runtimeId) {
        if (runtimeId == null || runtimeId.isBlank()) {
            return;
        }
        topologyLock.writeLock().lock();
        try {
            Runtime runtime = runtimes.remove(runtimeId);
            if (runtime != null) {
                unregister(runtime, false);
            }
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    public void accept(EngineEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof DeviceEvent deviceEvent) {
            acceptDevice(deviceEvent);
            return;
        }
        if (event instanceof TimeEvent timeEvent) {
            acceptTime(timeEvent);
            return;
        }
        throw new IllegalArgumentException("unsupported engine event: " + event.getClass().getName());
    }

    public Optional<Runtime> runtime(String runtimeId) {
        return Optional.ofNullable(runtimes.get(runtimeId));
    }

    public int runtimeCount() {
        return runtimes.size();
    }

    private void acceptDevice(DeviceEvent event) {
        topologyLock.readLock().lock();
        try {
            EvalUpdate update = evalForest.accept(event.getKey(), event.getValue());
            if (!update.changed()) {
                return;
            }
            Map<String, Set<String>> groupsByRuntime = new LinkedHashMap<>();
            update.changedResults().keySet().forEach(rootKey -> groupsByRuntime
                    .computeIfAbsent(rootKey.runtimeId(), ignored -> ConcurrentHashMap.newKeySet())
                    .add(rootKey.conditionGroupId()));
            groupsByRuntime.forEach((runtimeId, groupIds) -> {
                Runtime runtime = runtimes.get(runtimeId);
                if (runtime == null || !runtime.isActiveAt(lifecycleManager.now())) {
                    return;
                }
                Set<String> candidates = runtime.actionGroupIdsForDeviceGroups(groupIds);
                if (!candidates.isEmpty()) {
                    runtimeScheduler.schedule(runtime, RuntimeSignal.stateChanged(candidates));
                }
            });
        } finally {
            topologyLock.readLock().unlock();
        }
    }

    private void acceptTime(TimeEvent event) {
        topologyLock.readLock().lock();
        try {
            Runtime runtime = runtimes.get(event.key().runtimeId());
            if (runtime == null || !runtime.isActiveAt(lifecycleManager.now())) {
                return;
            }
            RuntimeSignal signal = signalRouter.route(runtime, event);
            if (signal != null) {
                runtimeScheduler.schedule(runtime, signal);
            }
        } finally {
            topologyLock.readLock().unlock();
        }
    }

    private void activate(Runtime runtime) {
        topologyLock.writeLock().lock();
        try {
            if (runtimes.get(runtime.runtimeId()) != runtime || !runtime.activate()) {
                return;
            }
            timeScheduleService.track(runtime, this::accept);
            // Pending 期间 Forest 仍持续更新；激活时基于最新现实状态完整推演一次。
            RuntimeSignal activationSignal = signalRouter.routeActivation(runtime);
            if (activationSignal != null) {
                runtimeScheduler.schedule(runtime, activationSignal);
            }
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private void expire(Runtime runtime) {
        topologyLock.writeLock().lock();
        try {
            if (!runtimes.remove(runtime.runtimeId(), runtime)) {
                return;
            }
            unregister(runtime, true);
        } finally {
            topologyLock.writeLock().unlock();
        }
    }

    private void unregister(Runtime runtime, boolean expired) {
        lifecycleManager.cancel(runtime.runtimeId());
        timeScheduleService.cancel(runtime.runtimeId());
        runtimeScheduler.cancel(runtime.runtimeId());
        if (expired) {
            runtime.expire();
        }
        runtime.close();
    }
}
