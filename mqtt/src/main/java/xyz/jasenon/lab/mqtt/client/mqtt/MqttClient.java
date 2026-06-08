package xyz.jasenon.lab.mqtt.client.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.command.seq.SeqGenerator;
import xyz.jasenon.lab.common.command.seq.SeqGeneratorManager;
import xyz.jasenon.lab.mqtt.client.AbstractSysClient;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class MqttClient extends AbstractSysClient<MqttTask> {

    private static final Logger log = LoggerFactory.getLogger(MqttClient.class);
    public final String sendTopic;
    public final String acceptTopic;

    public MqttClient(String serverURI, String clientId, String gatewayId, String sendTopic, String acceptTopic) throws MqttException {
        super(serverURI, clientId, gatewayId);
        this.sendTopic = sendTopic;
        this.acceptTopic = acceptTopic;
    }

    @Override
    protected void send(MqttTask mqttTask) {
        log.info("req:{}",mqttTask.getPayload());
        try {
            publish(sendTopic, new MqttMessage(mqttTask.getPayload()));
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected <RESP extends Task> boolean match(MqttTask mqttTask, RESP resp) {
        return matches(mqttTask, resp);
    }

    static boolean matches(MqttTask mqttTask, Task resp) {
        if (mqttTask == null || resp == null) {
            return false;
        }
        if (!Objects.equals(mqttTask.getGatewayId(), resp.getGatewayId())) {
            return false;
        }
        if (mqttTask.getCommandLine() == null) {
            return false;
        }

        SeqGenerator reqGenerator = SeqGeneratorManager.get(mqttTask.getCommandLine().getReqSeq());
        SeqGenerator respGenerator = SeqGeneratorManager.get(mqttTask.getCommandLine().getRespSeq());
        if (reqGenerator == null || respGenerator == null) {
            return false;
        }

        try {
            String reqSeq = reqGenerator.generate(mqttTask.getPayload());
            String respSeq = respGenerator.generate(resp.getPayload());
            return Objects.equals(reqSeq, respSeq);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected void onMessage(Object resp) {

    }

    @Override
    protected void onResponse(Object resp) {

    }

    @Override
    protected void onTimeout(MqttTask mqttTask, TimeoutException e) {

    }

    @Override
    protected void onError(MqttTask mqttTask, Exception e) {

    }
}
