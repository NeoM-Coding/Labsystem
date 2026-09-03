package xyz.jasenon.lab.bi.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.bi.api.model.EduTimeRange;
import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;
import xyz.jasenon.lab.bi.api.view.EduMetric;
import xyz.jasenon.lab.bi.api.view.EduProgressSummary;
import xyz.jasenon.lab.bi.api.view.SemesterBrief;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class EduDashboardServiceImplTests {

    private FakeJdbc jdbc;
    private EduDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbc = new FakeJdbc();
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-03T02:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        service = new EduDashboardServiceImpl(jdbc, clock);
        UserContextHolder.set(UserContext.builder()
                .userId("operator-1")
                .laboratoryIds(List.of("lab-1", "lab-2"))
                .build());
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void monthRangeUsesOneTimestampAndIntersectsRequestedLaboratories() {
        SemesterBrief semester = new SemesterBrief(
                "semester-1", "2026-2027 第1学期",
                LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 20)
        );
        jdbc.semester = semester;
        EduProgressSummary summary = new EduProgressSummary(
                new EduMetric(10, 20), new EduMetric(4, 8),
                new EduMetric(1, 2), new EduMetric(5, 10), 1
        );
        jdbc.summary = summary;

        var result = service.dashboard(new EduDashboardQuery(
                EduTimeRange.CURRENT_MONTH,
                List.of("lab-2", "invisible-lab")
        )).data();

        assertThat(result.calculatedAt().toString()).isEqualTo("2026-09-03T10:00");
        assertThat(result.summary()).isEqualTo(summary);
        assertThat(result.timeline()).hasSize(30);
        assertThat(result.weekdays()).hasSize(7);
        assertThat(jdbc.summaryParameters.getValue("laboratoryIds")).isEqualTo(List.of("lab-2"));
        assertThat(jdbc.summaryParameters.getValue("periodStart")).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(jdbc.summaryParameters.getValue("periodEnd")).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void emptyVisibleScopeReturnsZerosWithoutQueryingTheView() {
        UserContextHolder.set(UserContext.builder().userId("operator-1").laboratoryIds(List.of()).build());
        var result = service.dashboard(new EduDashboardQuery(EduTimeRange.TODAY, List.of())).data();

        assertThat(result.summary()).isEqualTo(EduProgressSummary.zero());
        assertThat(result.weekdays()).hasSize(7);
    }

    private static final class FakeJdbc extends NamedParameterJdbcTemplate {

        private SemesterBrief semester;
        private EduProgressSummary summary;
        private MapSqlParameterSource summaryParameters;

        private FakeJdbc() {
            super(new JdbcTemplate());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, SqlParameterSource parameters, RowMapper<T> rowMapper) {
            if (sql.contains("FROM semester")) {
                return semester == null ? List.of() : List.of((T) semester);
            }
            return List.of();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, SqlParameterSource parameters, RowMapper<T> rowMapper) {
            summaryParameters = (MapSqlParameterSource) parameters;
            return (T) summary;
        }
    }
}
