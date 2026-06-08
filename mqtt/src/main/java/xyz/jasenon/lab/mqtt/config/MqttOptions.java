package xyz.jasenon.lab.mqtt.config;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "mqtt")
public class MqttOptions {

    private static final String DEFAULT_URL = "tcp://localhost:1883";
    private static final MqttQoS DEFAULT_QOS = MqttQoS.AT_LEAST_ONCE;

    private String username = "";

    private String password = "";

    private String url = DEFAULT_URL;

    private MqttQoS mqttQoS = DEFAULT_QOS;

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public void setPassword(String password) {
        this.password = password == null ? "" : password;
    }

    public void setUrl(String url) {
        this.url = url == null || url.isBlank() ? DEFAULT_URL : url;
    }

    public void setMqttQoS(MqttQoS mqttQoS) {
        this.mqttQoS = mqttQoS == null ? DEFAULT_QOS : mqttQoS;
    }
}
