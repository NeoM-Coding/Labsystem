package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record ContactUserCreate(
        String name,
        String phone,
        String email,
        String mark
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        String displayName = name == null || name.isBlank() ? "未命名" : name.trim();
        return "创建联系人「" + displayName + "」";
    }
}
