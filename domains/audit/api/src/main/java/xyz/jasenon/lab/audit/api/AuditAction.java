package xyz.jasenon.lab.audit.api;

public enum AuditAction {
    CREATE("创建"),
    EDIT("编辑"),
    DELETE("删除");

    private final String displayName;

    AuditAction(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
