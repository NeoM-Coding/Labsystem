package xyz.jasenon.lab.mqtt.client.message_handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.jasenon.lab.common.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.common.event.RuleEngineChannels;
import xyz.jasenon.lab.common.model.device.BaseRecord;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Duration;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageHandlerSnapshotPublishTests {

    @Test
    void persistPublishesRuleEngineSnapshot() throws Exception {
        MessagePersistent<TestRecord> persistent = mock(MessagePersistent.class);
        RedisBus redisBus = mock(RedisBus.class);
        when(persistent.persist(any(TestRecord.class))).thenReturn(true);

        TestHandler handler = new TestHandler(persistent, redisBus);
        handler.persist("sensor-1", new byte[]{42});

        verify(redisBus).hsetex(anyString(), anyMap(), any(Duration.class));
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisBus).publish(org.mockito.Mockito.eq(RuleEngineChannels.DEVICE_RECORD_CHANGE), messageCaptor.capture());
        verify(persistent).persist(any(TestRecord.class));

        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        DeviceRecordSnapshotEvent event = objectMapper.readValue(messageCaptor.getValue(), DeviceRecordSnapshotEvent.class);
        assertEquals(DeviceType.Sensor, event.getDeviceType());
        assertEquals("sensor-1", event.getDeviceId());
        assertEquals("42", event.getRecordFields().get("temperature"));
        assertTrue(event.getRecordFields().containsKey("deviceId"));
    }

    private static class TestHandler extends MessageHandler<TestRecord> {

        TestHandler(MessagePersistent<TestRecord> persistent, RedisBus jedis) {
            super(persistent, jedis, DeviceType.Sensor);
        }

        @Override
        protected TestRecord decode(byte[] payload) {
            TestRecord record = new TestRecord();
            record.setTemperature(payload[0]);
            return record;
        }
    }

    private static class TestRecord extends BaseRecord {

        private int temperature;

        public int getTemperature() {
            return temperature;
        }

        public void setTemperature(int temperature) {
            this.temperature = temperature;
        }
    }
}
