package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;
import java.time.LocalDate;

public record SemesterBrief(
        String id,
        String name,
        LocalDate startDate,
        LocalDate endDate
) implements Serializable {
}
