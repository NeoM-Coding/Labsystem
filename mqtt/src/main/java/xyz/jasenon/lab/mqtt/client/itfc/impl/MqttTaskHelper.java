package xyz.jasenon.lab.mqtt.client.itfc.impl;

import org.springframework.stereotype.Service;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

@Service
public class MqttTaskHelper implements TaskHelper {
    @Override
    public MqttTask help(MqttTask.Dto dto) {
        return null;
    }
}
