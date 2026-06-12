package xyz.jasenon.lab.engine;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.SetQueue;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.EventKey;
import xyz.jasenon.lab.engine.event.EventTable;
import xyz.jasenon.lab.engine.runtime.LoggingRuntimeExecutor;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeExecutor;
import xyz.jasenon.lab.engine.runtime.RuntimeTable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class Engine {

    private final EventTable<Set<String>> eventHelper = new EventTable<>();
    private final RuntimeTable runtimeHelper = new RuntimeTable();
    private final SetQueue<String> readyQueue = new SetQueue<>(ConcurrentHashMap.newKeySet(), new LinkedBlockingQueue<>());
    private final RuntimeExecutor runtimeExecutor;
    private final ExecutorService runtimeTaskExecutor;
    private final Semaphore readySignal = new Semaphore(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread dispatcherThread;

    public Engine() {
        this(new LoggingRuntimeExecutor(), false);
    }

    @Autowired
    public Engine(RuntimeExecutor runtimeExecutor) {
        this(runtimeExecutor, false);
    }

    Engine(RuntimeExecutor runtimeExecutor, boolean autoStart) {
        this.runtimeExecutor = runtimeExecutor;
        this.runtimeTaskExecutor = Executors.newFixedThreadPool(
                Math.max(2, java.lang.Runtime.getRuntime().availableProcessors() / 2),
                namedThreadFactory("rule-engine-runtime")
        );
        if (autoStart) {
            start();
        }
    }

    @PostConstruct
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        dispatcherThread = new Thread(this::dispatchLoop, "rule-engine-ready-dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    @PreDestroy
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        readySignal.release();
        if (dispatcherThread != null) {
            dispatcherThread.interrupt();
        }
        runtimeTaskExecutor.shutdownNow();
    }

    public void register(Runtime runtime) {
        runtimeHelper.register(runtime);
        for (EventKey key : runtime.getRoots().keys()) {
            eventHelper.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(runtime.getRuntimeId());
        }
    }

    public void remove(String runtimeId) {
        Runtime runtime = runtimeHelper.remove(runtimeId);
        if (runtime == null) {
            return;
        }
        for (EventKey key : runtime.getRoots().keys()) {
            eventHelper.get(key).ifPresent(runtimeIds -> runtimeIds.remove(runtimeId));
        }
        readyQueue.discard(runtimeId);
    }

    public void accept(DeviceEvent event) {
        Set<String> runtimeIds = eventHelper.getOrDefault(event.eventKey(), Set.of());
        for (String runtimeId : runtimeIds) {
            runtimeHelper.get(runtimeId).ifPresent(runtime -> accept(runtime, event));
        }
    }

    public String pollReady() {
        return readyQueue.poll();
    }

    public boolean releaseReady(String runtimeId) {
        return readyQueue.remove(runtimeId);
    }

    public int readySize() {
        return readyQueue.size();
    }

    public int activeReadySize() {
        return readyQueue.activeSize();
    }

    public void drainReady() {
        String runtimeId;
        while ((runtimeId = readyQueue.poll()) != null) {
            submitRuntimeTask(runtimeId);
        }
    }

    RuntimeTable runtimeTable() {
        return runtimeHelper;
    }

    private void accept(Runtime runtime, DeviceEvent event) {
        for (var leaf : runtime.leaves(event.eventKey())) {
            leaf.refreshLeaf(event.getValue());
        }
        if (readyQueue.offer(runtime.getRuntimeId())) {
            readySignal.release();
        }
    }

    private void dispatchLoop() {
        while (running.get()) {
            try {
                readySignal.acquire();
                drainReady();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void submitRuntimeTask(String runtimeId) {
        try {
            runtimeTaskExecutor.submit(() -> runRuntimeInference(runtimeId));
        } catch (RejectedExecutionException e) {
            readyQueue.remove(runtimeId);
            throw e;
        }
    }

    private void runRuntimeInference(String runtimeId) {
        try {
            runtimeHelper.get(runtimeId).ifPresent(this::executeSatisfiedActionGroups);
        } finally {
            readyQueue.remove(runtimeId);
        }
    }

    private void executeSatisfiedActionGroups(Runtime runtime) {
        for (ActionGroup actionGroup : runtime.getActionGroups()) {
            if (actionGroup.getRoot().isResult()) {
                runtimeExecutor.execute(runtime, actionGroup);
            }
        }
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
