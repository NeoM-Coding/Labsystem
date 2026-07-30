package xyz.jasenon.lab.base.api.dto;

import java.io.Serial;
import java.io.Serializable;

public record UserListQuery(String keyword) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
