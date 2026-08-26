package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;
import java.util.Set;

public record LaboratoryViewerUpdate(String laboratoryId, Set<String> userIds) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LaboratoryViewerUpdate {
        userIds = userIds == null ? Set.of() : Set.copyOf(userIds);
    }

    @Override
    public String log() {
        return "更新实验室「" + laboratoryId + "」的可见成员，共 " + userIds.size() + " 人";
    }
}
