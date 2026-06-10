package xyz.jasenon.lab.mqtt.client.message_handler.itfc;

import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.common.model.device.records.CircuitBreakRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;

@Mapper
public interface CircuitBreakMessagePersistent extends MessagePersistent<CircuitBreakRecord> {
}
