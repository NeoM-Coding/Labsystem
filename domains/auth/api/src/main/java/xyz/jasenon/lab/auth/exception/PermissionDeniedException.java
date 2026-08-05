package xyz.jasenon.lab.auth.exception;

import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.common.exception.BusinessException;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(SourceType entityType, String entityId, String permission) {
        super(403, "无权访问资源: " + entityType + ":" + entityId + "#" + permission);
    }
}
