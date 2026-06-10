package xyz.jasenon.lab.mqtt.client.message_handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.model.device.BaseRecord;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MessageHandlerManager {
    private static final Map<DeviceType, MessageHandler<? extends BaseRecord>> HANDLERS = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(MessageHandlerManager.class);

    public static void register(MessageHandler<? extends BaseRecord> handler){
        if (handler == null){
            return;
        }
        HANDLERS.put(handler.deviceType, handler);
    }

    public static MessageHandler<? extends BaseRecord> get(DeviceType type){
        return HANDLERS.get(type);
    }

    public static void persist(PendingRequest<MqttTask> task, byte[] payload){
        // 这里只处理轮询的数据
        if (task.getType() != PendingRequest.Type.POLL) return;
        MessageDto dto = MessageDto.from(task);

        var handler = HANDLERS.get(dto.deviceType);
        if (handler == null) {
            log.warn("[MessageHandlerManager] type:{} 's handler not found, please check it!", dto.deviceType);
            return;
        };

        handler.persist(dto.deviceId, payload);
    }


    public static class MessageDto {
        private final DeviceType deviceType;
        private final String deviceId;

        private MessageDto(DeviceType deviceType, String deviceId){
            this.deviceType = deviceType;
            this.deviceId = deviceId;
        }

        public static MessageDto from(PendingRequest<MqttTask> task){
            return new MessageDto(task.getRequest().getType(), task.getRequest().getDeviceId());
        }

        public DeviceType getDeviceType() {
            return deviceType;
        }

        public String getDeviceId() {
            return deviceId;
        }
    }

}
