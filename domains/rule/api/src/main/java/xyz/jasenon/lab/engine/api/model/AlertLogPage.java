package xyz.jasenon.lab.engine.api.model;

import java.io.Serializable;
import java.util.List;

public record AlertLogPage(
        List<AlertLog> records,
        long total,
        long current,
        long size,
        long pages
) implements Serializable {

    public AlertLogPage {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
