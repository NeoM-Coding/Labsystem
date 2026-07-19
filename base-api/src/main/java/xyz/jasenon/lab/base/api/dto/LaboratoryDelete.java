package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record LaboratoryDelete(
        String laboratoryId,
        String laboratoryName
) implements Loggable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String log() {
        String name = laboratoryName == null || laboratoryName.isBlank() ? laboratoryId : laboratoryName.trim();
        return "删除实验室「" + name + "」";
    }
}
