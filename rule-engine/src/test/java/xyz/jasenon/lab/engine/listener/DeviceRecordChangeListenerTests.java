package xyz.jasenon.lab.engine.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.jasenon.lab.common.event.DeviceRecordSnapshotEvent;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DeviceRecordChangeListenerTests {

    @Test
    void firstSnapshotPublishesAllFields() {
        Engine engine = mock(Engine.class);
        DeviceRecordChangeListener listener = new DeviceRecordChangeListener(engine, mock(RedisBus.class), new ObjectMapper());

        listener.accept(snapshot(Map.of("opened", "true", "roomTemperature", "27")));

        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(engine, org.mockito.Mockito.times(2)).accept(captor.capture());
        List<String> fields = captor.getAllValues().stream().map(DeviceEvent::getField).sorted().toList();
        assertEquals(List.of("opened", "roomTemperature"), fields);
    }

    @Test
    void nextSnapshotPublishesOnlyChangedAndNewFields() {
        Engine engine = mock(Engine.class);
        DeviceRecordChangeListener listener = new DeviceRecordChangeListener(engine, mock(RedisBus.class), new ObjectMapper());
        listener.accept(snapshot(ordered("opened", "true", "roomTemperature", "27")));

        org.mockito.Mockito.clearInvocations(engine);
        listener.accept(snapshot(ordered("opened", "true", "roomTemperature", "28", "errorCode", "0")));

        ArgumentCaptor<DeviceEvent> captor = ArgumentCaptor.forClass(DeviceEvent.class);
        verify(engine, org.mockito.Mockito.times(2)).accept(captor.capture());
        List<String> fields = captor.getAllValues().stream().map(DeviceEvent::getField).sorted().toList();
        assertEquals(List.of("errorCode", "roomTemperature"), fields);
        verifyNoMoreInteractions(engine);
    }

    @Test
    void replaysCachedSnapshotForLateRuntimeRegistration() {
        Engine engine = mock(Engine.class);
        DeviceRecordChangeListener listener = new DeviceRecordChangeListener(
                engine,
                mock(RedisBus.class),
                new ObjectMapper()
        );
        listener.accept(snapshot(ordered("opened", "true", "roomTemperature", "27")));
        clearInvocations(engine);

        listener.replay(DeviceType.AirCondition, "ac-1");

        verify(engine, org.mockito.Mockito.times(2)).accept(
                org.mockito.ArgumentMatchers.any(DeviceEvent.class)
        );
    }

    @Test
    void restoresReplayStateFromMqttRedisHash() {
        Engine engine = mock(Engine.class);
        RedisBus redisBus = mock(RedisBus.class);
        when(redisBus.hgetAll("record:AirCondition:ac-1"))
                .thenReturn(ordered("opened", "true", "roomTemperature", "27"));
        DeviceRecordChangeListener listener = new DeviceRecordChangeListener(
                engine,
                redisBus,
                new ObjectMapper()
        );

        listener.replay(DeviceType.AirCondition, "ac-1");

        verify(redisBus).hgetAll("record:AirCondition:ac-1");
        verify(engine, org.mockito.Mockito.times(2)).accept(
                org.mockito.ArgumentMatchers.any(DeviceEvent.class)
        );
    }

    private static DeviceRecordSnapshotEvent snapshot(Map<String, String> fields) {
        return new DeviceRecordSnapshotEvent(DeviceType.AirCondition, "ac-1", fields, Instant.now());
    }

    private static Map<String, String> ordered(String... values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put(values[i], values[i + 1]);
        }
        return result;
    }
}
