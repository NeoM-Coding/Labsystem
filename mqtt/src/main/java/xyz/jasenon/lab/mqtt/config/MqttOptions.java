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
    private static final long DEFAULT_POLL_INTERVAL_MILLIS = 2_000L;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 5_000L;
    private static final long DEFAULT_POLL_WATCHDOG_INTERVAL_MILLIS = 60_000L;
    private static final long DEFAULT_GATEWAY_WATCHDOG_INTERVAL_MILLIS = 60_000L;

    private Connect connect = new Connect();

    private Poll poll = new Poll();

    private Gateway gateway = new Gateway();

    @Getter
    @Setter
    public static class Connect {

        private String username = "";

        private String password = "";

        private String url = DEFAULT_URL;

        private MqttQoS qos = DEFAULT_QOS;

        public void setUsername(String username) {
            this.username = username == null ? "" : username;
        }

        public void setPassword(String password) {
            this.password = password == null ? "" : password;
        }

        public void setUrl(String url) {
            this.url = url == null || url.isBlank() ? DEFAULT_URL : url;
        }

        public void setQos(MqttQoS qos) {
            this.qos = qos == null ? DEFAULT_QOS : qos;
        }
    }

    @Getter
    @Setter
    public static class Poll {

        private long intervalMillis = DEFAULT_POLL_INTERVAL_MILLIS;

        private long timeoutMillis = DEFAULT_POLL_TIMEOUT_MILLIS;

        private long watchdogIntervalMillis = DEFAULT_POLL_WATCHDOG_INTERVAL_MILLIS;

        public void setIntervalMillis(long intervalMillis) {
            this.intervalMillis = positiveOrDefault(intervalMillis, DEFAULT_POLL_INTERVAL_MILLIS);
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = positiveOrDefault(timeoutMillis, DEFAULT_POLL_TIMEOUT_MILLIS);
        }

        public void setWatchdogIntervalMillis(long watchdogIntervalMillis) {
            this.watchdogIntervalMillis = positiveOrDefault(
                    watchdogIntervalMillis,
                    DEFAULT_POLL_WATCHDOG_INTERVAL_MILLIS
            );
        }
    }

    @Getter
    @Setter
    public static class Gateway {

        private long watchdogIntervalMillis = DEFAULT_GATEWAY_WATCHDOG_INTERVAL_MILLIS;

        public void setWatchdogIntervalMillis(long watchdogIntervalMillis) {
            this.watchdogIntervalMillis = positiveOrDefault(
                    watchdogIntervalMillis,
                    DEFAULT_GATEWAY_WATCHDOG_INTERVAL_MILLIS
            );
        }
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value <= 0 ? defaultValue : value;
    }
}
