package xyz.jasenon.lab.auth.exception;

import xyz.jasenon.lab.auth.SourceType;

public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(SourceType entityType, String entityId, String permission) {
        super("无权访问资源: " + entityType + ":" + entityId + "#" + permission);
    }
}
