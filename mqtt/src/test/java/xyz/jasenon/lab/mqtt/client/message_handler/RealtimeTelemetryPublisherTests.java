package xyz.jasenon.lab.mqtt.client.message_handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import xyz.jasenon.lab.common.realtime.RealtimeChannels;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.device.model.records.SensorRecord;
import xyz.jasenon.lab.mqtt.config.MqttOptions;
import xyz.jasenon.lab.redis.core.RedisBus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class RealtimeTelemetryPublisherTests {

    private RealtimeTelemetryPublisher publisher;

    @AfterEach
    void tearDown() {
        if (publisher != null) publisher.close();
    }

    @Test
    void coalescesLatestTelemetryPerDevice() throws Exception {
        RedisBus redisBus = mock(RedisBus.class);
        MqttOptions options = new MqttOptions();
        options.getRealtime().setCoalesceWindowMillis(30);
        publisher = new RealtimeTelemetryPublisher(redisBus, options);

        publisher.publish(DeviceType.Sensor, record("device-1", "lab-1", 20));
        publisher.publish(DeviceType.Sensor, record("device-1", "lab-1", 27));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(redisBus, timeout(500).times(1)).publish(eq(RealtimeChannels.EVENTS), json.capture());
        JsonNode message = JsonMapper.builder().findAndAddModules().build().readTree(json.getValue());
        assertThat(message.path("audienceType").asText()).isEqualTo("LABORATORY");
        assertThat(message.path("audienceIds").get(0).asText()).isEqualTo("lab-1");
        assertThat(message.path("event").path("eventType").asText()).isEqualTo("device.telemetry.updated");
        assertThat(message.path("event").path("data").path("temperature").asDouble()).isEqualTo(27);
    }

    @Test
    void keepsDifferentDevicesIndependent() {
        RedisBus redisBus = mock(RedisBus.class);
        MqttOptions options = new MqttOptions();
        options.getRealtime().setCoalesceWindowMillis(20);
        publisher = new RealtimeTelemetryPublisher(redisBus, options);

        publisher.publish(DeviceType.Sensor, record("device-1", "lab-1", 20));
        publisher.publish(DeviceType.Sensor, record("device-2", "lab-1", 21));

        verify(redisBus, timeout(500).times(2)).publish(eq(RealtimeChannels.EVENTS), org.mockito.ArgumentMatchers.anyString());
    }

    private static SensorRecord record(String deviceId, String laboratoryId, double temperature) {
        SensorRecord record = SensorRecord.builder().temperature(temperature).build();
        record.setDeviceId(deviceId);
        record.setLaboratoryId(laboratoryId);
        return record;
    }
}
