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
}
