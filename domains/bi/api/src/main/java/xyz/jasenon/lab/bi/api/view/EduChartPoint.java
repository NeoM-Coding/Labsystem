package xyz.jasenon.lab.bi.api.view;

import java.io.Serializable;

public record EduChartPoint(
        String key,
        String label,
        EduMetric metric
) implements Serializable {
}
