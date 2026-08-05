package xyz.jasenon.lab.mqtt.client.message_handler.itfc;

import org.apache.ibatis.annotations.Mapper;
import xyz.jasenon.lab.device.model.records.AccessRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.MessagePersistent;

@Mapper
public interface AccessMessagePersistent extends MessagePersistent<AccessRecord> {

}
