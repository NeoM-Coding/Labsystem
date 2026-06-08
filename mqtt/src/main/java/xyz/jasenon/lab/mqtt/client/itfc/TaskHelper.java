package xyz.jasenon.lab.mqtt.client.itfc;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

public interface TaskHelper {

    MqttTask help(MqttTaskDto dto);

}
