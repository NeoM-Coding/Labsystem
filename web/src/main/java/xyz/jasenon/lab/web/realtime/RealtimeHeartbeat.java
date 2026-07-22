package xyz.jasenon.lab.web.realtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class RealtimeHeartbeat {

    private final RealtimeSessionRegistry registry;
    private final RealtimeWebSocketProperties properties;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "websocket-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public RealtimeHeartbeat(RealtimeSessionRegistry registry, RealtimeWebSocketProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(
                registry::heartbeat,
                properties.getPingIntervalMillis(),
                properties.getPingIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void close() {
        scheduler.shutdownNow();
    }
}
