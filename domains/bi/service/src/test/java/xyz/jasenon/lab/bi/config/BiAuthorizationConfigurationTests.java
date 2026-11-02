package xyz.jasenon.lab.bi.config;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.permission.Action;
import xyz.jasenon.lab.bi.api.model.EduTimeRange;
import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiAuthorizationConfigurationTests {

    @Test
    void dashboardQueryRequiresEduDataAnalysisPermission() {
        var handler = new BiAuthorizationConfiguration().eduDashboardAuthorization();
        var context = UserContext.builder().userId("operator-1").build();

        var command = handler.handle(new EduDashboardQuery(EduTimeRange.TODAY, List.of()), context);

        assertThat(command.entityType()).isEqualTo(SourceType.app);
        assertThat(command.entityId()).isEqualTo("global");
        assertThat(command.action()).isEqualTo(Action.App.edu_data_analysis);
        assertThat(command.subjectId()).isEqualTo("operator-1");
    }
    @Test
    void telemetryOptionsAndReportsRequireTheirOwnAnalysisPermission() {
        var handler=new BiAuthorizationConfiguration().telemetryAuthorization();
        var context=UserContext.builder().userId("analyst").build();
        for(var kind:xyz.jasenon.lab.bi.api.query.TelemetryQuery.Kind.values()) {
            var query=new xyz.jasenon.lab.bi.api.query.TelemetryQuery(kind,null,null,null,List.of());
            var command=handler.handle(query,context);
            assertThat(command.action()).isEqualTo(kind==xyz.jasenon.lab.bi.api.query.TelemetryQuery.Kind.AIR_CONDITION
                ? Action.App.air_condition_data_analysis:Action.App.circuit_break_data_analysis);
            assertThat(command.entityId()).isEqualTo("global");
            assertThat(command.subjectId()).isEqualTo("analyst");
        }
    }
}
