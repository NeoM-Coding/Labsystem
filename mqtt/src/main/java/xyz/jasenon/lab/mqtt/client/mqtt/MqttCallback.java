package xyz.jasenon.lab.mqtt.client.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.util.AsyncExecutor;
import xyz.jasenon.lab.mqtt.client.SysClientManager;
import xyz.jasenon.lab.mqtt.client.message_handler.MessageHandlerManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MqttCallback implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttCallback.class);
    private static final int MAX_RETRY_TIMES = 5;
    private static final long INITIAL_BACKOFF_MILLIS = 1_000L;

    private final MqttClient client;
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    public MqttCallback(MqttClient client) {
        this.client = client;
    }

    /**
     * 指数回避  最多回避5次
     * 超次后从 {@link SysClientManager} remove this.client
     * 交由 Manager中的watchdog 重新拉起
     */
    @Override
    public void connectComplete(boolean b, String s) {
        reconnecting.set(false);
        if (subscribeWithBackoff()) {
            log.info("gateway-id:{} connected, subscribed topic:{}", client.gatewayId, client.acceptTopic);
            return;
        }

        SysClientManager.remove(client);
    }

    /**
     * 指数回避  最多回避5次
     * 超次后从 {@link SysClientManager} remove自己
     * 交由 Manager中的watchdog 重新拉起
     * @param throwable
     */
    @Override
    public void connectionLost(Throwable throwable) {
        log.warn("gateway-id:{} connection lost", client.gatewayId, throwable);
        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(this::reconnectWithBackoff);
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        byte[] payload = mqttMessage.getPayload();
        client.receive(new Task(client.gatewayId, payload));

        // 后置处理消息持久化
        var task = client.current();
        if (MqttTask.Explainer.verifier(task.getRequest()
                .getCommandLine().getCommand().getCheckType(), payload)){
            MessageHandlerManager.persist(task, payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }

    private void reconnectWithBackoff() {
        for (int retryTimes = 1; retryTimes <= MAX_RETRY_TIMES; retryTimes++) {
            if (client.isConnected()) {
                reconnecting.set(false);
                return;
            }

            sleep(backoffMillis(retryTimes));

            try {
                client.reconnect();
                return;
            } catch (MqttException e) {
                log.warn(
                        "gateway-id:{} reconnect failed, retry:{}/{}",
                        client.gatewayId,
                        retryTimes,
                        MAX_RETRY_TIMES,
                        e
                );
            }
        }

        reconnecting.set(false);
        SysClientManager.remove(client);
    }

    private boolean subscribeWithBackoff() {
        for (int retryTimes = 1; retryTimes <= MAX_RETRY_TIMES; retryTimes++) {
            try {
                client.subscribe(client.acceptTopic);
                return true;
            } catch (MqttException e) {
                log.warn(
                        "gateway-id:{} subscribe topic:{} failed, retry:{}/{}",
                        client.gatewayId,
                        client.acceptTopic,
                        retryTimes,
                        MAX_RETRY_TIMES,
                        e
                );
                sleep(backoffMillis(retryTimes));
            }
        }
        return false;
    }

    private long backoffMillis(int retryTimes) {
        return INITIAL_BACKOFF_MILLIS << (retryTimes - 1);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
