package xyz.jasenon.lab.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lab.auth.permify")
public class PermifyAuthProperties {

    private String baseUrl = "http://localhost:3476";
    private String tenantId = "t1";
    private String schemaVersion = "";
    private int depth = 20;
    private long lookupPageSize = 100;

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
        // Permify rejects permission graph traversal depths below 3.
        this.depth = depth < 3 ? 20 : depth;
    }

    public long getLookupPageSize() {
        return lookupPageSize;
    }

    public void setLookupPageSize(long lookupPageSize) {
        this.lookupPageSize = lookupPageSize <= 0 ? 100 : lookupPageSize;
    }
}
