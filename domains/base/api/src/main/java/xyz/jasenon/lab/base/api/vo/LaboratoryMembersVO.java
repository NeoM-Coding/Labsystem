package xyz.jasenon.lab.base.api.vo;

import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record LaboratoryMembersVO(List<User> owners, List<User> viewers) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public LaboratoryMembersVO {
        owners = owners == null ? List.of() : List.copyOf(owners);
        viewers = viewers == null ? List.of() : List.copyOf(viewers);
    }
}
