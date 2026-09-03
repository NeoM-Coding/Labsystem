package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;

public record EduMetric(long courseCount, long teachingHours) implements Serializable {

    public static EduMetric zero() {
        return new EduMetric(0, 0);
    }
}
