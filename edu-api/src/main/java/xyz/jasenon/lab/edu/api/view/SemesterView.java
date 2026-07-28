package xyz.jasenon.lab.edu.api.view;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SemesterView(
        String id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createAt,
        LocalDateTime updateAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
