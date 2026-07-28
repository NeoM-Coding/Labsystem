package xyz.jasenon.lab.edu.api.command;

import xyz.jasenon.lab.audit.api.Loggable;

import java.io.Serial;

public record TimetableImport(
        String semesterId,
        String laboratoryId,
        String filename,
        byte[] content
) implements Loggable {
    @Serial
    private static final long serialVersionUID = 1L;

    public TimetableImport {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }

    @Override
    public String log() {
        return "向实验室「" + laboratoryId + "」导入学期「" + semesterId + "」课表";
    }
}
