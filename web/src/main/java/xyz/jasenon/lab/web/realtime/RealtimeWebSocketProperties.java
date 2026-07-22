package xyz.jasenon.lab.web.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "lab.websocket")
public class RealtimeWebSocketProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:8989",
            "http://127.0.0.1:8989"
    ));
    private int sendTimeLimitMillis = 10_000;
    private int sendBufferBytes = 1_048_576;
    private long pingIntervalMillis = 25_000L;
    private long idleTimeoutMillis = 60_000L;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public int getSendTimeLimitMillis() {
        return sendTimeLimitMillis;
    }

    public void setSendTimeLimitMillis(int value) {
        this.sendTimeLimitMillis = value > 0 ? value : 10_000;
    }

    public int getSendBufferBytes() {
        return sendBufferBytes;
    }

    public void setSendBufferBytes(int value) {
        this.sendBufferBytes = value > 0 ? value : 1_048_576;
    }

    public long getPingIntervalMillis() {
        return pingIntervalMillis;
    }

    public void setPingIntervalMillis(long value) {
        this.pingIntervalMillis = value > 0 ? value : 25_000L;
    }

    public long getIdleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    public void setIdleTimeoutMillis(long value) {
        this.idleTimeoutMillis = value > 0 ? value : 60_000L;
    }
}
