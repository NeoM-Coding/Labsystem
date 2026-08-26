package xyz.jasenon.lab.base.compensation;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.base.api.model.CompensationTask;
import xyz.jasenon.lab.base.api.model.Laboratory;
import xyz.jasenon.lab.base.api.model.User;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CompensationTaskSchedulerTests {
    @Test
    void calculatesNextMidnightInConfiguredZone() {
        CompensationTask task = new CompensationTask();
        task.setCronExpression("0 0 0 * * *"); task.setZoneId("Asia/Shanghai");
        assertThat(CompensationTaskScheduler.nextFire(task,
                LocalDateTime.of(2026, 8, 25, 23, 30)))
                .isEqualTo(LocalDateTime.of(2026, 8, 26, 0, 0));
    }

    @Test
    void extractsOnlyValidManagerUserIds() {
        Laboratory lab = new Laboratory();
        User first = new User(); first.setId("user-1");
        User blank = new User(); blank.setId(" ");
        lab.setManager(List.of(first, blank));
        assertThat(LaboratoryAuthorizationReconcileHandler.managerIds(lab))
                .containsExactly("user-1");
    }
}
