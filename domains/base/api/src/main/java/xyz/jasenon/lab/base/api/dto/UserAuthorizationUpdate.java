package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.auth.permission.RelationShip;
import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.util.Set;

public record UserAuthorizationUpdate(
        User user,
        Set<RelationShip.App> appRelations,
        Set<String> laboratoryIds
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        String displayName = user == null || user.getName() == null || user.getName().isBlank()
                ? userId()
                : user.getName().trim();
        return "编辑用户「" + displayName + "」并同步 "
                + sizeOf(appRelations) + " 项应用权限、"
                + sizeOf(laboratoryIds) + " 个实验室范围";
    }

    private String userId() {
        return user == null || user.getId() == null || user.getId().isBlank()
                ? "未知用户"
                : user.getId();
    }

    private static int sizeOf(Set<?> values) {
        return values == null ? 0 : values.size();
    }
}
