package xyz.jasenon.lab.engine.time;

import java.time.Instant;
import java.util.Optional;

/**
 * 从属于可复用 TimeConditionGroup、可以计算下一时间边界的条件。
 */
public interface TimeCondition {

    String conditionId();

    boolean isWindow();

    boolean isWindowActive(Instant instant);

    Optional<TimeTransition> nextTransitionAfter(Instant instant);
}
