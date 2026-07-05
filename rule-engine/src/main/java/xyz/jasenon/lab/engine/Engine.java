package xyz.jasenon.lab.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventHandler;
import xyz.jasenon.lab.engine.event.EngineEvent;
import xyz.jasenon.lab.engine.event.EventKey;
import xyz.jasenon.lab.engine.event.EventTable;
import xyz.jasenon.lab.engine.event.RuntimeEventRouter;
import xyz.jasenon.lab.engine.event.TimeEvent;
import xyz.jasenon.lab.engine.event.TimeEventHandler;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeLifecycleManager;
import xyz.jasenon.lab.engine.runtime.RuntimeScheduler;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;
import xyz.jasenon.lab.engine.runtime.RuntimeState;
import xyz.jasenon.lab.engine.runtime.RuntimeTable;
import xyz.jasenon.lab.engine.time.TimeScheduleService;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎入口，只负责 Runtime 管理和事件路由。
 *
 * <p>生命周期定时、时间边界计算以及异步推演均委托给独立组件，
 * 避免 Engine 再次持有线程池或动作执行细节。</p>
 */
@Component
public class Engine {

    private final EventTable<Set<String>> eventHelper = new EventTable<>();
    private final RuntimeTable runtimeHelper = new RuntimeTable();
    private final RuntimeScheduler runtimeScheduler;
    private final RuntimeLifecycleManager lifecycleManager;
    private final TimeScheduleService timeScheduleService;
    private final RuntimeEventRouter eventRouter;

    @Autowired
    public Engine(
            RuntimeScheduler runtimeScheduler,
            RuntimeLifecycleManager lifecycleManager,
            TimeScheduleService timeScheduleService,
            RuntimeEventRouter eventRouter
    ) {
        this.runtimeScheduler = Objects.requireNonNull(runtimeScheduler, "runtimeScheduler");
        this.lifecycleManager = Objects.requireNonNull(lifecycleManager, "lifecycleManager");
        this.timeScheduleService = Objects.requireNonNull(timeScheduleService, "timeScheduleService");
        this.eventRouter = Objects.requireNonNull(eventRouter, "eventRouter");
    }

    public Engine(RuntimeScheduler runtimeScheduler) {
        this(
                runtimeScheduler,
                new RuntimeLifecycleManager(),
                new TimeScheduleService(),
                new RuntimeEventRouter(new DeviceEventHandler(), new TimeEventHandler())
        );
    }

    public void register(Runtime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        remove(runtime.getRuntimeId());
        runtimeHelper.register(runtime);
        lifecycleManager.track(
                runtime,
                () -> activate(runtime),
                () -> expire(runtime)
        );
    }

    public void remove(String runtimeId) {
        Runtime runtime = runtimeHelper.remove(runtimeId);
        if (runtime == null) {
            return;
        }
        unregister(runtime, RuntimeState.CANCELLED);
    }

    public void accept(EngineEvent event) {
        Objects.requireNonNull(event, "event");
        if (event instanceof DeviceEvent deviceEvent) {
            acceptDevice(deviceEvent);
            return;
        }
        if (event instanceof TimeEvent timeEvent) {
            runtimeHelper.get(timeEvent.key().runtimeId())
                    .ifPresent(runtime -> accept(runtime, timeEvent));
            return;
        }
        throw new IllegalArgumentException("unsupported engine event: " + event.getClass().getName());
    }

    RuntimeTable runtimeTable() {
        return runtimeHelper;
    }

    private void activate(Runtime runtime) {
        if (runtimeHelper.get(runtime.getRuntimeId()).orElse(null) != runtime || !runtime.activate()) {
            return;
        }
        // PENDING Runtime 在真正激活前不会进入设备事件反向索引。
        for (EventKey key : runtime.getRoots().keys()) {
            eventHelper.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
                    .add(runtime.getRuntimeId());
        }
        if (timeScheduleService.track(runtime, this::accept)) {
            runtimeScheduler.schedule(runtime, RuntimeSignal.stateChanged());
        }
    }

    private void expire(Runtime runtime) {
        if (!runtimeHelper.remove(runtime.getRuntimeId(), runtime)) {
            return;
        }
        unregister(runtime, RuntimeState.EXPIRED);
    }

    private void unregister(Runtime runtime, RuntimeState terminalState) {
        lifecycleManager.cancel(runtime.getRuntimeId());
        timeScheduleService.cancel(runtime.getRuntimeId());
        for (EventKey key : runtime.getRoots().keys()) {
            // 原子更新索引，避免注销与同 EventKey 的新 Runtime 激活发生误删竞态。
            eventHelper.computeIfPresent(key, (ignored, runtimeIds) -> {
                runtimeIds.remove(runtime.getRuntimeId());
                return runtimeIds.isEmpty() ? null : runtimeIds;
            });
        }
        runtimeScheduler.cancel(runtime.getRuntimeId());
        if (terminalState == RuntimeState.EXPIRED) {
            runtime.expire();
        } else {
            runtime.cancel();
        }
    }

    private void acceptDevice(DeviceEvent event) {
        Set<String> runtimeIds = eventHelper.getOrDefault(event.eventKey(), Set.of());
        for (String runtimeId : runtimeIds) {
            runtimeHelper.get(runtimeId).ifPresent(runtime -> accept(runtime, event));
        }
    }

    private void accept(Runtime runtime, EngineEvent event) {
        var now = lifecycleManager.now();
        if (runtime.isExpiredAt(now)) {
            expire(runtime);
            return;
        }
        if (!runtime.isActiveAt(now)) {
            return;
        }
        RuntimeSignal signal = eventRouter.route(runtime, event);
        if (signal != null) {
            runtimeScheduler.schedule(runtime, signal);
        }
    }
}
