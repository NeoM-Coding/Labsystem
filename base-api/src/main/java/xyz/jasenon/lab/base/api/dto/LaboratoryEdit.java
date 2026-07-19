package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;
import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.util.List;
import java.util.Map;

public record LaboratoryEdit(
        String laboratoryId,
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
        String name = laboratoryName == null || laboratoryName.isBlank() ? laboratoryId : laboratoryName.trim();
        return "编辑实验室「" + name + "」";
    }
}
