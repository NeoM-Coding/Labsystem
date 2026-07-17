package xyz.jasenon.lab.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lab.observability.tracing")
public class TracingProperties {

    private boolean enabled = true;
    private int maxArgumentLength = 2048;
    private int maxCollectionSize = 20;
    private int maxDepth = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxArgumentLength() { return maxArgumentLength; }
    public void setMaxArgumentLength(int maxArgumentLength) { this.maxArgumentLength = maxArgumentLength; }
    public int getMaxCollectionSize() { return maxCollectionSize; }
    public void setMaxCollectionSize(int maxCollectionSize) { this.maxCollectionSize = maxCollectionSize; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
}
