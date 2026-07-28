package xyz.jasenon.lab.edu.api.model;

import java.io.Serializable;

public enum WeekType implements Serializable {
    Single(0, "单周"),
    Double(1, "双周"),
    Both(2, "单周以及双周");

    private final int value;
    private final String description;

    WeekType(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
