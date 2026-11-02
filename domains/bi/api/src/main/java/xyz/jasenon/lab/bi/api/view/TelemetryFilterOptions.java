package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;
import java.util.List;

public record TelemetryFilterOptions(
        List<Option> buildings, List<Option> organizations,
        List<Option> laboratories, List<Option> devices
) implements Serializable {
    public record Option(String value, String label, String description) implements Serializable {}
}
