package xyz.jasenon.lab.mqtt.client.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.mqtt.protocol.command.Task;
import xyz.jasenon.lab.common.util.AsyncExecutor;
import xyz.jasenon.lab.mqtt.client.SysClientManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttCallback implements org.eclipse.paho.client.mqttv3.MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(MqttCallback.class);
    private static final int MAX_RETRY_TIMES = 5;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000L;

    private final MqttClient client;
    private final MqttConnectOptions options;
    private final long initialBackoffMillis;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public MqttCallback(MqttClient client, MqttConnectOptions options) {
        this(client, options, INITIAL_BACKOFF_MILLIS);
    }

    MqttCallback(MqttClient client, MqttConnectOptions options, long initialBackoffMillis) {
        this.client = client;
        this.options = options;
        this.initialBackoffMillis = initialBackoffMillis;
    }

    @Override
    public void connectionLost(Throwable throwable) {
        log.warn("gateway-id:{} connection lost", client.gatewayId, throwable);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!client.isStopped() && reconnecting.compareAndSet(false, true)) {
            CompletableFuture.runAsync(this::reconnectWithBackoff);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        // 注意receive 对 client.current的消解问题
        var task = client.current();

        byte[] payload = mqttMessage.getPayload();
        if (task == null) {
            log.debug("ignore MQTT response without pending request, gateway-id:{}", client.gatewayId);
            return;
        }
        client.receive(new Task(client.gatewayId, payload));

        // 后置处理消息持久化

        if (MqttTask.Explainer.verifier(task.getRequest()
                .getCommandLine().getCommand().getCheckType(), payload)){
            AsyncExecutor.runAsyncIO(task.traceContext().wrap(() ->
                    xyz.jasenon.lab.observability.context.Tracing.operation("device.snapshot.process")
                            .attribute("gateway.id", client.gatewayId)
                            .run(() -> MessageHandlerManager.persist(task, payload))));
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }

    private void reconnectWithBackoff() {
        try {
            for (int attempt = 1; attempt <= MAX_RETRY_TIMES; attempt++) {
                Thread.sleep(initialBackoffMillis << (attempt - 1));
                if (client.isStopped()) {
                    return;
                }
                try {
                    client.connectAndSubscribe(options);
                    log.info("gateway-id:{} reconnected and subscribed topic:{}", client.gatewayId, client.acceptTopic);
                    return;
                } catch (MqttException e) {
                    log.warn("gateway-id:{} reconnect or subscribe failed, attempt:{}/{}",
                            client.gatewayId, attempt, MAX_RETRY_TIMES, e);
                }
            }
            log.error("gateway-id:{} reconnect attempts exhausted; watchdog will rebuild", client.gatewayId);
            SysClientManager.remove(client);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            SysClientManager.remove(client);
        } catch (RuntimeException e) {
            log.error("gateway-id:{} recovery failed; watchdog will rebuild", client.gatewayId, e);
            SysClientManager.remove(client);
        } finally {
            reconnecting.set(false);
            // A disconnect can arrive after SUBACK but before the recovery flag is reset.
            if (!client.isConnected()) {
                scheduleReconnect();
            }
        }
    }
}
