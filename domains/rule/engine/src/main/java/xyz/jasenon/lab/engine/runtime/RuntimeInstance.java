package xyz.jasenon.lab.engine.runtime;

import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;
import xyz.jasenon.lab.engine.eval.ConditionTree;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventKey;
import xyz.jasenon.lab.engine.event.TimeWindowStatusEvent;
import xyz.jasenon.lab.engine.event.TimePointEvent;
import xyz.jasenon.lab.engine.execution.ActionDispatcher;
import xyz.jasenon.lab.engine.execution.ExecutionContext;
import xyz.jasenon.lab.engine.execution.ExecutionRequest;
import xyz.jasenon.lab.engine.plan.RuntimePlan;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Binding;
import xyz.jasenon.lab.engine.trigger.TriggerPolicy;
import xyz.jasenon.lab.observability.context.Tracing;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * All mutable fields except cancellation fence are exclusively owned by one WorkerLoop.
 */
public final class RuntimeInstance {
    public final RuntimePlan plan;
    public final long generation;
    public final long timerToken;
    private final ExecutionContext context;
    private final Map<String, ConditionTree> trees = new LinkedHashMap<>();
    private final Map<DeviceEventKey, Set<String>> deviceIndex = new HashMap<>();
    private final Map<String, Set<String>> windowIndex = new HashMap<>();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Deque<ExecutionRequest> pending = new ArrayDeque<>();
    private final Set<String> reconcile = new LinkedHashSet<>();
    private final Map<String, Set<String>> pointIndex = new HashMap<>();
    private final Map<String, Instant> lastPoints = new HashMap<>();
    private final int capacity;
    private boolean active, executing;
    private long sequence;
    private Instant lastWindowAt = Instant.MIN;
    public RuntimeInstance(RuntimePlan plan, long generation, int capacity) {
        this.plan = plan;
        this.generation = generation;
        this.timerToken = generation;
        this.capacity = capacity;
        if (plan.bindings().size() > capacity)
            throw new IllegalArgumentException("binding count exceeds pending capacity");
        context = new ExecutionContext(plan.runtimeId(), generation, plan.lifetime(), new AtomicBoolean(true));
        Map<TreeKey, ConditionTree> sharedTrees = new HashMap<>();
        for (Binding b : plan.bindings()) {
            bindings.put(b.id(), b);
            var windows = plan.timeGroups().get(b.timeGroupId());
            List<String> ids = windows.stream().filter(w -> w.isWindow()).map(w -> windowKey(b.timeGroupId(), w.conditionId())).toList();
            List<String> pointIds = windows.stream().filter(w -> !w.isWindow()).map(w -> windowKey(b.timeGroupId(), w.conditionId())).toList();
            ConditionTree tree = sharedTrees.computeIfAbsent(new TreeKey(b.conditionGroupId(), b.timeGroupId()),
                    key -> new ConditionTree(plan.conditionGroups().get(b.conditionGroupId()), ids, pointIds));
            trees.put(b.id(), tree);
            tree.eventKeys().forEach(k -> deviceIndex.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(b.id()));
            pointIds.forEach(k -> pointIndex.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(b.id()));
            ids.forEach(k -> windowIndex.computeIfAbsent(k, x -> new LinkedHashSet<>()).add(b.id()));
        }
    }

    public static String windowKey(String group, String condition) {
        return group.length() + ":" + group + condition;
    }

    public boolean hasRoom() {
        return pending.size() < capacity;
    }

    public boolean valid() {
        return context.valid().get();
    }

    public boolean needsPump() {
        return valid() && active && !executing && (!pending.isEmpty() || !reconcile.isEmpty());
    }

    public void cancel() {
        context.valid().set(false);
        active = false;
        pending.clear();
        reconcile.clear();
    }

    public void observe(DeviceEvent event, Instant now) {
        Set<String> candidates = deviceIndex.getOrDefault(event.getKey(), Set.of());
        Map<String, Boolean> before = values(candidates);
        candidates.stream().map(trees::get).distinct().forEach(tree -> tree.observe(event.getKey(), event.getValue()));
        decide(candidates, before, false, "device", now);
    }

    public void windows(TimeWindowStatusEvent event, Instant now, boolean activation) {
        if (event.generation() != generation || event.timerToken() != timerToken || !valid() || event.occurredAt().isBefore(lastWindowAt))
            return;
        lastWindowAt = event.occurredAt();
        Set<String> candidates = new LinkedHashSet<>();
        event.statuses().keySet().forEach(k -> candidates.addAll(windowIndex.getOrDefault(k, Set.of())));
        if (activation) candidates.addAll(bindings.keySet());
        Map<String, Boolean> before = values(candidates);
        Set<String> changed = new LinkedHashSet<>();
        Map<ConditionTree, Boolean> applied = new IdentityHashMap<>();
        candidates.forEach(id -> {
                    if (applied.computeIfAbsent(trees.get(id), tree -> tree.windows(event.statuses()))) changed.add(id);
                }
        );
        if (activation) {
            active = !plan.lifetime().pending(now) && !plan.lifetime().expired(now);
            changed.addAll(candidates);
        }
        decide(changed, before, activation, "window", now);
    }

    private void activate(Instant now) {
        active = valid() && !plan.lifetime().pending(now) && !plan.lifetime().expired(now);
    }

    /** Apply and reset in one worker turn, including submission while the pulse is true. */
    public void pulse(TimePointEvent event, Instant now, Runnable submit) {
        pulse(event, now, false, submit);
    }

    public void pulse(TimePointEvent event, Instant now, boolean activation, Runnable submit) {
        if (event.generation() != generation || event.timerToken() != timerToken || !valid()
                || plan.lifetime().pending(event.scheduledAt())) return;
        Set<String> fresh = new LinkedHashSet<>();
        for (String id : event.conditionIds()) {
            if (pointIndex.containsKey(id) && event.scheduledAt().isAfter(lastPoints.getOrDefault(id, Instant.MIN))) {
                fresh.add(id);
                lastPoints.put(id, event.scheduledAt());
            }
        }
        Set<String> pulsedBindings = new LinkedHashSet<>();
        fresh.forEach(id -> pulsedBindings.addAll(pointIndex.get(id)));
        Set<String> candidates = new LinkedHashSet<>(pulsedBindings);
        event.statuses().keySet().forEach(id -> candidates.addAll(windowIndex.getOrDefault(id, Set.of())));
        if (activation) {
            activate(now);
            candidates.addAll(bindings.keySet());
        }
        Map<String, Boolean> before = values(candidates);
        Set<ConditionTree> affected = Collections.newSetFromMap(new IdentityHashMap<>());
        candidates.forEach(id -> affected.add(trees.get(id)));
        Set<ConditionTree> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            boolean applyWindows = !event.occurredAt().isBefore(lastWindowAt);
            if (applyWindows) {
                affected.forEach(tree -> { if (tree.windows(event.statuses())) changed.add(tree); });
                lastWindowAt = event.occurredAt();
            }
            affected.forEach(tree -> tree.pulses(fresh, true));
            Set<String> eligible = new LinkedHashSet<>();
            candidates.forEach(id -> {
                if (activation || changed.contains(trees.get(id)) || pulsedBindings.contains(id)) eligible.add(id);
            });
            decide(eligible, before, activation, "time-point", now);
            submit.run();
        } finally {
            affected.forEach(tree -> tree.pulses(fresh, false));
        }
    }

    private Map<String, Boolean> values(Collection<String> ids) {
        Map<String, Boolean> v = new HashMap<>();
        ids.forEach(id -> v.put(id, trees.get(id).value()));
        return v;
    }

    private void decide(Set<String> ids, Map<String, Boolean> before, boolean activation, String cause, Instant now) {
        if (plan.lifetime().expired(now)) {
            cancel();
            return;
        }
        if (!active || !valid() || plan.lifetime().pending(now)) return;
        List<ExecutionRequest.Match> edges = new ArrayList<>();
        for (String id : ids) {
            Binding b = bindings.get(id);
            if (!TriggerPolicy.matches(b.mode(), before.getOrDefault(id, false), trees.get(id).value(), activation))
                continue;
            if (b.mode() == TriggerMode.RECONCILE) reconcile.add(id);
            else edges.add(match(id));
        }
        if (!edges.isEmpty()) pending.addLast(request(edges, cause, now));
    }

    private ExecutionRequest.Match match(String id) {
        Binding b = bindings.get(id);
        return new ExecutionRequest.Match(b, plan.actionGroups().get(b.actionGroupId()));
    }

    private ExecutionRequest request(List<ExecutionRequest.Match> matches, String cause, Instant now) {
        return new ExecutionRequest(plan.runtimeId() + ":" + generation + ":" + (++sequence), context, matches, now, cause, Tracing.capture());
    }

    public void completed() {
        executing = false;
    }

    public void pump(ActionDispatcher dispatcher, Runnable completion, Instant now) {
        if (plan.lifetime().expired(now)) {
            cancel();
            return;
        }
        if (executing || !active || !valid()) return;
        ExecutionRequest edge = pending.peekFirst();
        if (edge != null) {
            if (dispatcher.submit(edge, completion)) {
                pending.removeFirst();
                executing = true;
            }
            return;
        }
        if (!reconcile.isEmpty()) {
            List<ExecutionRequest.Match> matches = reconcile.stream()
                    .filter(id -> trees.get(id).value()).map(this::match).toList();
            if (matches.isEmpty()) {
                reconcile.clear();
            } else if (dispatcher.submit(request(matches, "reconcile", now), completion)) {
                reconcile.clear();
                executing = true;
            }
        }
    }

    public RuntimeSnapshot snapshot(Instant now) {
        String state = !valid() ? (plan.lifetime().expired(now) ? "EXPIRED" : "CANCELLED") : plan.lifetime().expired(now) ? "EXPIRED" : active ? "ACTIVE" : "PENDING";
        return new RuntimeSnapshot(plan.runtimeId(), generation, state, values(bindings.keySet()), pending.size(), executing);
    }

    private record TreeKey(String deviceGroup, String timeGroup) {
    }
}
