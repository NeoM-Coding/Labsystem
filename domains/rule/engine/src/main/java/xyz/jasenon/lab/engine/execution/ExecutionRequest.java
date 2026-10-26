package xyz.jasenon.lab.engine.execution;

import xyz.jasenon.lab.engine.plan.RuntimePlan.ActionGroup;
import xyz.jasenon.lab.engine.plan.RuntimePlan.Binding;
import xyz.jasenon.lab.observability.context.Tracing;

import java.time.Instant;
import java.util.List;

public record ExecutionRequest(String decisionId, ExecutionContext context, List<Match> matches, Instant matchedAt,
                               String cause, Tracing.Propagation propagation) {
    public ExecutionRequest {
        matches = List.copyOf(matches);
    }

    public record Match(Binding binding, ActionGroup group) {
    }
}
