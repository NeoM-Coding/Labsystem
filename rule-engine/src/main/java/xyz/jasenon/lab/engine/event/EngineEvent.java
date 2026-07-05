package xyz.jasenon.lab.engine.event;

import java.time.Instant;

/**
 * 规则引擎可接收事件的统一契约。
 */
public interface EngineEvent {

    EventKey eventKey();

    Instant occurredAt();
}
