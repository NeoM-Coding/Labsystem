package xyz.jasenon.lab.engine.execution.notice;

@FunctionalInterface
public interface RuleExecutionNoticePublisher {
    static RuleExecutionNoticePublisher noop() {
        return ignored -> {
        }
                ;
    }

    void publish(RuleExecutionNotice notice);
}
