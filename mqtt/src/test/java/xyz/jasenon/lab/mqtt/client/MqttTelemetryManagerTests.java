package xyz.jasenon.lab.mqtt.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.DeviceTelemetrySnapshot;
import xyz.jasenon.lab.device.model.DeviceRecordKeys;
import xyz.jasenon.lab.device.model.records.SensorRecord;
import xyz.jasenon.lab.device.model.devices.Sensor;
import xyz.jasenon.lab.mqtt.client.itfc.DeviceHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.LatestDeviceRecordMapper;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttTelemetryManagerTests {

    private DeviceHelper deviceHelper;
    private LatestDeviceRecordMapper latestRecordMapper;
    private VisibleLaboratoryScope visibleLaboratoryScope;
    private RedisBus redisBus;
    private MqttTelemetryManager manager;

    @BeforeEach
    void setUp() {
        deviceHelper = mock(DeviceHelper.class);
        latestRecordMapper = mock(LatestDeviceRecordMapper.class);
        visibleLaboratoryScope = mock(VisibleLaboratoryScope.class);
        redisBus = mock(RedisBus.class);
        manager = new MqttTelemetryManager(
                deviceHelper, latestRecordMapper, visibleLaboratoryScope, redisBus
        );
        when(visibleLaboratoryScope.resolve(List.of("lab-1"))).thenReturn(List.of("lab-1"));
    }

    @Test
    void prefersTheRedisSnapshotAndRestoresTelemetryValueTypes() {
        Sensor sensor = sensor();
        String key = DeviceRecordKeys.recordKey(sensor.getDeviceType(), sensor.getId());
        when(deviceHelper.listByLaboratories(null, List.of("lab-1"))).thenReturn(List.of(sensor));
        when(redisBus.hgetAllBatch(List.of(key))).thenReturn(Map.of(key, Map.of(
                "__occurredAt", "2026-07-23T08:00:00Z",
                "temperature", "23.5",
                "opened", "true",
                "mode", "auto"
        )));

        DeviceTelemetrySnapshot snapshot = manager.snapshots(List.of("lab-1")).data().get(0);

        assertTrue(snapshot.online());
        assertEquals(23.5d, snapshot.record().get("temperature"));
        assertEquals(true, snapshot.record().get("opened"));
        assertEquals("auto", snapshot.record().get("mode"));
        assertNotNull(snapshot.occurredAt());
        verify(latestRecordMapper, never()).latestSensor(anyList());
    }

    @Test
    void fallsBackToTheLatestDatabaseRecordWhenRedisHasNoSnapshot() {
        Sensor sensor = sensor();
        String key = DeviceRecordKeys.recordKey(sensor.getDeviceType(), sensor.getId());
        SensorRecord record = SensorRecord.builder()
                .address(8)
                .selfId(1)
                .temperature(22.4)
                .humidity(46)
                .light(310)
                .smoke(0)
                .build();
        record.setDeviceId(sensor.getId());
        record.setCreateAt(LocalDateTime.of(2026, 7, 23, 16, 0));
        when(deviceHelper.listByLaboratories(null, List.of("lab-1"))).thenReturn(List.of(sensor));
        when(redisBus.hgetAllBatch(List.of(key))).thenReturn(Map.of(key, Map.of()));
        when(latestRecordMapper.latestSensor(List.of(sensor.getId()))).thenReturn(List.of(record));

        DeviceTelemetrySnapshot snapshot = manager.snapshots(List.of("lab-1")).data().get(0);

        assertFalse(snapshot.online());
        assertEquals(22.4d, snapshot.record().get("temperature"));
        assertEquals("device-1", snapshot.deviceId());
        assertEquals("lab-1", snapshot.laboratoryId());
    }

    private static Sensor sensor() {
        Sensor sensor = new Sensor();
        sensor.setId("device-1");
        sensor.setBelongTo("lab-1");
        sensor.setDeviceName("环境传感器");
        sensor.setGatewayId("gateway-1");
        sensor.setAddress(8);
        sensor.setSelfId(1);
        return sensor;
    }
}
