package xyz.jasenon.lab.engine.execution;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.action.*;
import xyz.jasenon.lab.engine.execution.notice.RuleExecutionNotice;
import xyz.jasenon.lab.engine.execution.notice.RuleExecutionNoticePublisher;
import xyz.jasenon.lab.engine.scheduling.EngineOptions;
import xyz.jasenon.lab.observability.context.TraceContext;
import xyz.jasenon.lab.observability.context.Tracing;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes already-decided immutable requests, never reads condition state.
 */
@Component
public final class ActionDispatcher implements AutoCloseable {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ActionDispatcher.class);
    private final ConcurrentHashMap<String, ExecutionRequest> inFlight = new ConcurrentHashMap<>();
    private final RuntimeExecutor executor;
    private final RuleExecutionNoticePublisher notices;
    private final ThreadPoolExecutor pool;

    public ActionDispatcher(RuntimeExecutor executor, RuleExecutionNoticePublisher notices, EngineOptions options) {
        this.executor = executor;
        this.notices = notices;
        pool = new ThreadPoolExecutor(options.getExecutionThreads(), options.getExecutionThreads(), 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(options.getWorkerCapacity()), r -> {
            Thread t = new Thread(r, "rule-v3-action");
            t.setDaemon(true);
            return t;
        }
                , new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean submit(ExecutionRequest request, Runnable completed) {
        if (inFlight.putIfAbsent(request.context().runtimeId(), request) != null) return false;
        Runnable done = () -> {
            inFlight.remove(request.context().runtimeId(), request);
            completed.run();
        };
        try {
            pool.execute(() -> request.propagation().get(() -> {
                        try {
                            execute(request).whenComplete((v, e) -> {
                                        if (e != null) log.warn("v3 execution failed {}", request.decisionId(), e);
                                        done.run();
                                    }
                            );
                        } catch (Throwable e) {
                            log.error("v3 execution failed {}", request.decisionId(), e);
                            done.run();
                        }
                        return null;
                    }
            ));
            return true;
        } catch (RejectedExecutionException e) {
            inFlight.remove(request.context().runtimeId(), request);
            return false;
        }
    }

    private CompletableFuture<Void> execute(ExecutionRequest request) {
        List<CompletableFuture<Void>> groups = new ArrayList<>();
        for (var match : request.matches()) {
            if (!request.context().isActiveAt(Instant.now())) break;
            List<Action> actions = match.group().actions().stream().map(s -> s.materialize(match.group().actionGroupId())).toList();
            List<CompletableFuture<ActionExecutionResult>> results = new ArrayList<>(Collections.nCopies(actions.size(), null));
            List<CompletableFuture<ActionExecutionResult>> configured = new ArrayList<>();
            String traceId = TraceContext.traceId();
            for (int i = 0; i < actions.size(); i++)
                if (!(actions.get(i) instanceof PollAction)) {
                    var f = invoke(request, match, actions.get(i));
                    results.set(i, f);
                    configured.add(f);
                }
            var completionContext = Tracing.capture();
            CompletableFuture<Void> group = CompletableFuture.allOf(configured.toArray(CompletableFuture[]::new))
                    .thenCompose(v -> completionContext.get(() -> {
                                List<CompletableFuture<ActionExecutionResult>> polls = new ArrayList<>();
                                for (int i = 0; i < actions.size(); i++)
                                    if (actions.get(i) instanceof PollAction && request.context().isActiveAt(Instant.now())) {
                                        var f = invoke(request, match, actions.get(i));
                                        results.set(i, f);
                                        polls.add(f);
                                    }
                                return CompletableFuture.allOf(polls.toArray(CompletableFuture[]::new));
                            }
                    )).thenRun(() -> completionContext.get(() -> {
                                publish(request, match, actions, results, traceId);
                                return null;
                            }
                    ));
            groups.add(group);
        }
        return CompletableFuture.allOf(groups.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<ActionExecutionResult> invoke(ExecutionRequest r, ExecutionRequest.Match m, Action a) {
        if (!r.context().isActiveAt(Instant.now()))
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(r.context().runtimeId(), m.group().actionGroupId(), a.is(), "runtime no longer active"));
        try {
            return Objects.requireNonNull(executor.execute(r.context(), m.group(), a)).handle((v, e) -> e == null && v != null ? v : ActionExecutionResult.failed(r.context().runtimeId(), m.group().actionGroupId(), a.is(), e == null ? "no result" : e.toString()));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(ActionExecutionResult.failed(r.context().runtimeId(), m.group().actionGroupId(), a.is(), e.toString()));
        }
    }

    private void publish(ExecutionRequest r, ExecutionRequest.Match m, List<Action> actions, List<CompletableFuture<ActionExecutionResult>> results, String traceId) {
        List<RuleExecutionNotice.ActionResult> summary = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            if (results.get(i) == null) continue;
            Action a = actions.get(i);
            var result = results.get(i).join();
            String target = a instanceof ControlAction c ? c.getControl().getDeviceId() : a instanceof PollAction p ? p.query().getDeviceId() : null;
            List<String> users = a instanceof ReportAction report ? report.getUserIds() : List.of();
            Set<String> types = a instanceof ReportAction report ? report.getTypes().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()) : Set.of();
            summary.add(new RuleExecutionNotice.ActionResult(i, result.actionType(), target, users, types, a instanceof ReportAction report ? report.getContent() : null, result.status(), result.message(), result.completedAt()));
        }
        try {
            notices.publish(new RuleExecutionNotice(r.decisionId() + ":" + m.binding().id(), r.context().runtimeId(), m.group().actionGroupId(), m.binding().conditionGroupId(), m.binding().timeGroupId(), r.matchedAt(), Instant.now(), traceId, summary));
        } catch (RuntimeException e) {
            log.warn("v3 notice failed {}", r.decisionId(), e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        pool.shutdownNow();
    }
}
