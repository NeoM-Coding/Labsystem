package xyz.jasenon.lab.bi.api.query;

import xyz.jasenon.lab.bi.api.model.EduTimeRange;

import java.io.Serializable;
import java.util.List;

public record EduDashboardQuery(
        EduTimeRange range,
        List<String> laboratoryIds
) implements Serializable {
}
