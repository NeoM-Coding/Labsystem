package xyz.jasenon.lab.base.api.vo;

import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.model.User;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LaboratoryVO(
        String id,
        String buildingName,
        String orgName,
        String laboratoryName,
        Map<String, Object> extra,
        List<User> managers,
        LocalDateTime createAt,
        LocalDateTime updateAt
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static LaboratoryVO from(Laboratory laboratory) {
        Objects.requireNonNull(laboratory, "laboratory");
        List<User> managers = laboratory.getManager() == null
                ? List.of()
                : laboratory.getManager().stream()
                        .filter(Objects::nonNull)
                        .map(User::mask)
                        .toList();
        return new LaboratoryVO(
                laboratory.getId(),
                laboratory.getBuildingName(),
                laboratory.getOrgName(),
                laboratory.getLaboratoryName(),
                laboratory.getExtra(),
                managers,
                laboratory.getCreateAt(),
                laboratory.getUpdateAt()
        );
    }
}
