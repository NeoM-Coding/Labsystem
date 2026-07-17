package xyz.jasenon.lab.mqtt.client.message_handler;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import xyz.jasenon.lab.device.model.BaseRecord;

public interface MessagePersistent<R extends BaseRecord> extends BaseMapper<R> {

    default boolean persist(R record) {
        return insert(record) > 0;
    }

}
