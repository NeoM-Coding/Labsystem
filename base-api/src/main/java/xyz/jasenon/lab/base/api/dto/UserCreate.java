package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.auth.permission.RelationShip;
import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;
import java.util.Set;

public record UserCreate(
        String name,
        String username,
        String password,
        String phone,
        String email,
        String mark,
        Set<RelationShip.App> appRelations,
        Set<String> laboratoryIds
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "创建用户「" + userLabel(name, username) + "」并配置 "
                + sizeOf(appRelations) + " 项应用权限、"
                + sizeOf(laboratoryIds) + " 个实验室范围";
    }

    private static String userLabel(String name, String username) {
        String displayName = hasText(name) ? name.trim() : "未命名";
        return hasText(username) ? displayName + "（" + username.trim() + "）" : displayName;
    }

    private static int sizeOf(Set<?> values) {
        return values == null ? 0 : values.size();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
