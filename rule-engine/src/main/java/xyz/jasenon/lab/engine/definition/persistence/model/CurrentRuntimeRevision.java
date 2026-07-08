package xyz.jasenon.lab.engine.definition.persistence.model;

/**
 * 元数据与当前发布 revision 的联表投影。
 */
public class CurrentRuntimeRevision {

    private String runtimeId;
    private Boolean enabled;
    private String definition;

    public CurrentRuntimeRevision() {
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public void setRuntimeId(String runtimeId) {
        this.runtimeId = runtimeId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }
}
