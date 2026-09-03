package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;

public record EduProgressSummary(
        EduMetric total,
        EduMetric completed,
        EduMetric active,
        EduMetric pending,
        long activeLaboratoryCount
) implements Serializable {

    public static EduProgressSummary zero() {
        EduMetric zero = EduMetric.zero();
        return new EduProgressSummary(zero, zero, zero, zero, 0);
    }
}
