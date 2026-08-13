package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record UserDelete(
        String userId,
        String displayName
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        String target = displayName == null || displayName.isBlank() ? userId : displayName.trim();
        return "删除用户或联系人「" + target + "」";
    }
}
