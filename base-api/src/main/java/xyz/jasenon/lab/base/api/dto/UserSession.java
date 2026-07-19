package xyz.jasenon.lab.base.api.dto;

import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.io.Serializable;

public record UserSession(
        User user,
        String tokenName,
        String tokenValue
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
