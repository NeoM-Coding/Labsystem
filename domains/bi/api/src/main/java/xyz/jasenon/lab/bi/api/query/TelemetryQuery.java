package xyz.jasenon.lab.bi.api.query;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** Empty dimensions mean all authorized values; values within a dimension are ORed. */
public record TelemetryQuery(
        Kind kind, Range range, LocalDate startDate, LocalDate endDate,
        List<String> laboratoryIds, List<String> buildingNames, List<String> orgNames,
        List<String> deviceIds
) implements Serializable {
    public TelemetryQuery(Kind kind, Range range, LocalDate startDate, LocalDate endDate,
                          List<String> laboratoryIds) {
        this(kind, range, startDate, endDate, laboratoryIds, List.of(), List.of(), List.of());
    }
    public enum Kind { AIR_CONDITION, ENERGY }
    public enum Range { TODAY, CURRENT_MONTH, CUSTOM }
}
