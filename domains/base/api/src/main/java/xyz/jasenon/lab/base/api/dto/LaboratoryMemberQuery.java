package xyz.jasenon.lab.base.api.dto;

import java.io.Serial;
import java.io.Serializable;

public record LaboratoryMemberQuery(String laboratoryId) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
