package xyz.jasenon.lab.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lab.auth.permify")
public class PermifyAuthProperties {

    private boolean enabled;
    private String baseUrl = "http://localhost:3476";
    private String tenantId = "t1";
    private String schemaVersion = "";
    private int depth = 20;
    private long lookupPageSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth <= 0 ? 20 : depth;
    }

    public long getLookupPageSize() {
        return lookupPageSize;
    }

    public void setLookupPageSize(long lookupPageSize) {
        this.lookupPageSize = lookupPageSize <= 0 ? 100 : lookupPageSize;
    }
}
