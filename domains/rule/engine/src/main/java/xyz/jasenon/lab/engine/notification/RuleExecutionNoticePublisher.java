package xyz.jasenon.lab.engine.notification;

@FunctionalInterface
public interface RuleExecutionNoticePublisher {

    void publish(RuleExecutionNotice notice);

    static RuleExecutionNoticePublisher noop() {
        return ignored -> { };
    }
}
