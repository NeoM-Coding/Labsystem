package xyz.jasenon.lab.mqtt.client.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.mqtt.client.SysClientManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttRecoveryTests {
    @Test
    void failedRecoveryRetiresClientAfterFiveAttempts() throws MqttException, InterruptedException {
        var attempts = new AtomicInteger();
        var closed = new CountDownLatch(1);
        var client = new MqttClient("tcp://localhost:1883", "recovery-test", "recovery-test", "send", "receive") {
            @Override
            public void connectAndSubscribe(MqttConnectOptions options) throws MqttException {
                attempts.incrementAndGet();
                throw new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT);
            }

            @Override
            public void close(boolean force) throws MqttException {
                super.close(force);
                closed.countDown();
            }
        };
        SysClientManager.register(client);
        try {
            new MqttCallback(client, new MqttConnectOptions(), 0).connectionLost(null);

            assertTrue(closed.await(5, TimeUnit.SECONDS));
            assertEquals(5, attempts.get());
            assertTrue(client.isStopped());
            assertFalse(client.isConnected());
        } finally {
            SysClientManager.remove(client);
        }
    }
}
