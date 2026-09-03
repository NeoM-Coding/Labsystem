package xyz.jasenon.lab.bi.service;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.bi.api.EduDashboardService;
import xyz.jasenon.lab.bi.api.model.EduTimeRange;
import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;
import xyz.jasenon.lab.bi.api.view.EduActiveCourse;
import xyz.jasenon.lab.bi.api.view.EduChartPoint;
import xyz.jasenon.lab.bi.api.view.EduDashboardView;
import xyz.jasenon.lab.bi.api.view.EduMetric;
import xyz.jasenon.lab.bi.api.view.EduProgressSummary;
import xyz.jasenon.lab.bi.api.view.SemesterBrief;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@DubboService
@Traced("edu-dashboard-service")
public class EduDashboardServiceImpl implements EduDashboardService {

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;

    private static final String SEMESTER_AT_DATE_SQL = """
            SELECT id, name, start_date, end_date
            FROM semester
            WHERE delete_at IS NULL
              AND :date BETWEEN start_date AND end_date
            ORDER BY start_date DESC
            LIMIT 1
            """;

    private final NamedParameterJdbcOperations jdbc;
    private final Clock clock;

    public EduDashboardServiceImpl(NamedParameterJdbcOperations jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @ActionAuthorized
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RpcResult<EduDashboardView> dashboard(EduDashboardQuery query) {
        if (query == null || query.range() == null) {
            throw new BusinessException(BAD_REQUEST, "统计时间维度不能为空");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        UserContext context = requireContext();
        List<String> laboratories = effectiveLaboratories(context, query.laboratoryIds());
        SemesterBrief semester = currentSemester(now.toLocalDate());
        Period period = period(query.range(), now.toLocalDate(), semester);

        if (laboratories.isEmpty()) {
            return RpcResult.success(emptyView(query.range(), period, semester, now));
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("periodStart", period.start())
                .addValue("periodEnd", period.end())
                .addValue("asOf", now)
                .addValue("laboratoryIds", laboratories);
        String semesterPredicate = "";
        if (query.range() == EduTimeRange.CURRENT_SEMESTER) {
            parameters.addValue("semesterId", Objects.requireNonNull(semester).id());
            semesterPredicate = " AND semester_id = :semesterId";
        }
        String scopePredicate = """
                 WHERE course_date BETWEEN :periodStart AND :periodEnd
                   AND laboratory_id IN (:laboratoryIds)
                """ + semesterPredicate;

        EduProgressSummary summary = summary(scopePredicate, parameters);
        List<EduChartPoint> timeline = timeline(query.range(), period, scopePredicate, parameters);
        List<EduChartPoint> weekdays = weekdays(scopePredicate, parameters);
        List<EduChartPoint> sections = sections(scopePredicate, parameters);
        List<EduActiveCourse> activeCourses = activeCourses(
                scopePredicate, parameters, laboratoryNames(context)
        );

        return RpcResult.success(new EduDashboardView(
                query.range(), period.start(), period.end(), semester, summary,
                timeline, weekdays, sections, activeCourses, now
        ));
    }

    private EduProgressSummary summary(String predicate, MapSqlParameterSource parameters) {
        String sql = """
                SELECT
                    COUNT(*) AS total_courses,
                    COALESCE(SUM(end_section - start_section + 1), 0) AS total_hours,
                    COALESCE(SUM(CASE WHEN end_at <= :asOf THEN 1 ELSE 0 END), 0) AS completed_courses,
                    COALESCE(SUM(CASE WHEN end_at <= :asOf THEN end_section - start_section + 1 ELSE 0 END), 0) AS completed_hours,
                    COALESCE(SUM(CASE WHEN start_at <= :asOf AND end_at > :asOf THEN 1 ELSE 0 END), 0) AS active_courses,
                    COALESCE(SUM(CASE WHEN start_at <= :asOf AND end_at > :asOf THEN end_section - start_section + 1 ELSE 0 END), 0) AS active_hours,
                    COALESCE(SUM(CASE WHEN start_at > :asOf THEN 1 ELSE 0 END), 0) AS pending_courses,
                    COALESCE(SUM(CASE WHEN start_at > :asOf THEN end_section - start_section + 1 ELSE 0 END), 0) AS pending_hours,
                    COUNT(DISTINCT CASE WHEN start_at <= :asOf AND end_at > :asOf THEN laboratory_id END) AS active_laboratories
                FROM bi_edu_course_occurrence
                """ + predicate;
        return jdbc.queryForObject(sql, parameters, (rs, rowNum) -> new EduProgressSummary(
                metric(rs, "total_courses", "total_hours"),
                metric(rs, "completed_courses", "completed_hours"),
                metric(rs, "active_courses", "active_hours"),
                metric(rs, "pending_courses", "pending_hours"),
                rs.getLong("active_laboratories")
        ));
    }

    private List<EduChartPoint> timeline(EduTimeRange range, Period period,
                                         String predicate, MapSqlParameterSource parameters) {
        String keyExpression;
        String labelExpression;
        switch (range) {
            case TODAY -> {
                keyExpression = "DATE_FORMAT(start_at, '%H:%i')";
                labelExpression = keyExpression;
            }
            case CURRENT_MONTH -> {
                keyExpression = "DATE_FORMAT(course_date, '%Y-%m-%d')";
                labelExpression = "DATE_FORMAT(course_date, '%m-%d')";
            }
            case CURRENT_SEMESTER -> {
                keyExpression = "CAST(week_number AS CHAR)";
                labelExpression = "CONCAT('第', week_number, '周')";
            }
            default -> throw new IllegalStateException("Unsupported range: " + range);
        }
        String sql = "SELECT " + keyExpression + " AS bucket_key, " + labelExpression + " AS bucket_label, " +
                "COUNT(*) AS course_count, COALESCE(SUM(end_section - start_section + 1), 0) AS teaching_hours " +
                "FROM bi_edu_course_occurrence " + predicate +
                " GROUP BY bucket_key, bucket_label ORDER BY MIN(start_at)";
        List<EduChartPoint> actual = jdbc.query(sql, parameters, (rs, rowNum) -> point(rs));
        return fillTimeline(range, period, actual);
    }

    private List<EduChartPoint> weekdays(String predicate, MapSqlParameterSource parameters) {
        String sql = """
                SELECT CAST(weekday AS CHAR) AS bucket_key,
                       CASE weekday
                         WHEN 1 THEN '周一' WHEN 2 THEN '周二' WHEN 3 THEN '周三'
                         WHEN 4 THEN '周四' WHEN 5 THEN '周五' WHEN 6 THEN '周六'
                         ELSE '周日' END AS bucket_label,
                       COUNT(*) AS course_count,
                       COALESCE(SUM(end_section - start_section + 1), 0) AS teaching_hours
                FROM bi_edu_course_occurrence
                """ + predicate + " GROUP BY weekday ORDER BY weekday";
        Map<String, EduChartPoint> actual = index(jdbc.query(sql, parameters, (rs, rowNum) -> point(rs)));
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<EduChartPoint> result = new ArrayList<>(7);
        for (int index = 1; index <= 7; index++) {
            String key = Integer.toString(index);
            result.add(actual.getOrDefault(key, new EduChartPoint(key, labels[index - 1], EduMetric.zero())));
        }
        return result;
    }

    private List<EduChartPoint> sections(String predicate, MapSqlParameterSource parameters) {
        String sql = """
                SELECT CONCAT(start_section, '-', end_section) AS bucket_key,
                       CONCAT(start_section, '-', end_section, '节') AS bucket_label,
                       COUNT(*) AS course_count,
                       COALESCE(SUM(end_section - start_section + 1), 0) AS teaching_hours
                FROM bi_edu_course_occurrence
                """ + predicate + " GROUP BY start_section, end_section ORDER BY start_section, end_section";
        return jdbc.query(sql, parameters, (rs, rowNum) -> point(rs));
    }

    private List<EduActiveCourse> activeCourses(String predicate, MapSqlParameterSource parameters,
                                                Map<String, String> laboratoryNames) {
        String sql = """
                SELECT timetable_id, semester_id, laboratory_id, course_name, teacher_name, start_at, end_at
                FROM bi_edu_course_occurrence
                """ + predicate + " AND start_at <= :asOf AND end_at > :asOf ORDER BY start_at, laboratory_id";
        return jdbc.query(sql, parameters, (rs, rowNum) -> new EduActiveCourse(
                rs.getString("timetable_id"),
                rs.getString("semester_id"),
                rs.getString("laboratory_id"),
                laboratoryNames.get(rs.getString("laboratory_id")),
                rs.getString("course_name"),
                rs.getString("teacher_name"),
                rs.getTimestamp("start_at").toLocalDateTime(),
                rs.getTimestamp("end_at").toLocalDateTime()
        ));
    }

    private SemesterBrief currentSemester(LocalDate date) {
        List<SemesterBrief> semesters = jdbc.query(
                SEMESTER_AT_DATE_SQL,
                new MapSqlParameterSource("date", date),
                (rs, rowNum) -> new SemesterBrief(
                        rs.getString("id"), rs.getString("name"),
                        rs.getDate("start_date").toLocalDate(),
                        rs.getDate("end_date").toLocalDate()
                )
        );
        return semesters.isEmpty() ? null : semesters.get(0);
    }

    private static Period period(EduTimeRange range, LocalDate today, SemesterBrief semester) {
        return switch (range) {
            case TODAY -> new Period(today, today);
            case CURRENT_MONTH -> {
                YearMonth month = YearMonth.from(today);
                yield new Period(month.atDay(1), month.atEndOfMonth());
            }
            case CURRENT_SEMESTER -> {
                if (semester == null) {
                    throw new BusinessException(NOT_FOUND, "当前日期不在任何有效学期内");
                }
                yield new Period(semester.startDate(), semester.endDate());
            }
        };
    }

    private static List<String> effectiveLaboratories(UserContext context, List<String> requested) {
        Set<String> visible = new LinkedHashSet<>(normalize(context.filterLaboratoryIds()));
        List<String> filters = normalize(requested);
        if (filters.isEmpty()) {
            return List.copyOf(visible);
        }
        return filters.stream().filter(visible::contains).toList();
    }

    private static Map<String, String> laboratoryNames(UserContext context) {
        if (context.getLaboratoryScopes() == null) {
            return Map.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        context.getLaboratoryScopes().stream()
                .filter(Objects::nonNull)
                .filter(scope -> scope.getLaboratoryId() != null)
                .forEach(scope -> names.put(scope.getLaboratoryId(), scope.getLaboratoryName()));
        return names;
    }

    private static List<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static UserContext requireContext() {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new BusinessException(UNAUTHORIZED, "登陆已过期");
        }
        return context;
    }

    private static EduMetric metric(ResultSet rs, String countColumn, String hoursColumn) throws SQLException {
        return new EduMetric(rs.getLong(countColumn), rs.getLong(hoursColumn));
    }

    private static EduChartPoint point(ResultSet rs) throws SQLException {
        return new EduChartPoint(
                rs.getString("bucket_key"),
                rs.getString("bucket_label"),
                metric(rs, "course_count", "teaching_hours")
        );
    }

    private static Map<String, EduChartPoint> index(List<EduChartPoint> points) {
        Map<String, EduChartPoint> result = new LinkedHashMap<>();
        points.forEach(point -> result.put(point.key(), point));
        return result;
    }

    private static List<EduChartPoint> fillTimeline(EduTimeRange range, Period period,
                                                    List<EduChartPoint> actualPoints) {
        if (range == EduTimeRange.TODAY) {
            return actualPoints;
        }
        Map<String, EduChartPoint> actual = index(actualPoints);
        List<EduChartPoint> result = new ArrayList<>();
        if (range == EduTimeRange.CURRENT_MONTH) {
            for (LocalDate date = period.start(); !date.isAfter(period.end()); date = date.plusDays(1)) {
                String key = date.toString();
                result.add(actual.getOrDefault(key,
                        new EduChartPoint(key, String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()), EduMetric.zero())));
            }
            return result;
        }
        long weeks = Math.min(60, ChronoUnit.WEEKS.between(
                period.start().minusDays(period.start().getDayOfWeek().getValue() - 1L),
                period.end().plusDays(7L - period.end().getDayOfWeek().getValue())
        ) + 1L);
        for (int week = 1; week <= weeks; week++) {
            String key = Integer.toString(week);
            result.add(actual.getOrDefault(key,
                    new EduChartPoint(key, "第" + week + "周", EduMetric.zero())));
        }
        return result;
    }

    private static EduDashboardView emptyView(EduTimeRange range, Period period,
                                              SemesterBrief semester, LocalDateTime now) {
        List<EduChartPoint> weekdays = new ArrayList<>(7);
        String[] labels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int index = 1; index <= 7; index++) {
            weekdays.add(new EduChartPoint(Integer.toString(index), labels[index - 1], EduMetric.zero()));
        }
        return new EduDashboardView(
                range, period.start(), period.end(), semester, EduProgressSummary.zero(),
                fillTimeline(range, period, List.of()), weekdays, List.of(), List.of(), now
        );
    }

    private record Period(LocalDate start, LocalDate end) {
    }
}
