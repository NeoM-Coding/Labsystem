package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.util.List;
import java.util.Map;

public record LaboratoryCreate(
        String buildingName,
        String orgName,
        String laboratoryName,
        Map<String, Object> extra,
        List<User> manager
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        return "创建实验室「" + displayName(laboratoryName) + "」";
    }

    private static String displayName(String value) {
        return value == null || value.isBlank() ? "未命名" : value.trim();
    }
}
