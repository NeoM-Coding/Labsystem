package xyz.jasenon.lab.engine.scheduling;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lab.rule-engine.v3")
public class EngineOptions {
    private int workers = Math.max(1, java.lang.Runtime.getRuntime().availableProcessors() - 1);
    private int ingressCapacity = 8192, workerCapacity = 8192, pendingCapacity = 1024, executionThreads = Math.max(2, java.lang.Runtime.getRuntime().availableProcessors() / 2);

    public int getWorkers() {
        return workers;
    }

    public void setWorkers(int n) {
        workers = positive(n);
    }

    public int getIngressCapacity() {
        return ingressCapacity;
    }

    public void setIngressCapacity(int n) {
        ingressCapacity = positive(n);
    }

    public int getWorkerCapacity() {
        return workerCapacity;
    }

    public void setWorkerCapacity(int n) {
        workerCapacity = positive(n);
    }

    public int getPendingCapacity() {
        return pendingCapacity;
    }

    public void setPendingCapacity(int n) {
        pendingCapacity = positive(n);
    }

    public int getExecutionThreads() {
        return executionThreads;
    }

    public void setExecutionThreads(int n) {
        executionThreads = positive(n);
    }

    private int positive(int n) {
        if (n < 1) throw new IllegalArgumentException("capacity must be positive");
        return n;
    }
}
