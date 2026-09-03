package xyz.jasenon.lab.web.bi;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.bi.api.EduDashboardService;
import xyz.jasenon.lab.bi.api.model.EduTimeRange;
import xyz.jasenon.lab.bi.api.view.EduDashboardView;
import xyz.jasenon.lab.bi.api.view.EduProgressSummary;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EduDashboardControllerTests {

    @Test
    void convertsHttpFiltersToSerializableRpcQuery() {
        EduDashboardService service = mock(EduDashboardService.class);
        LocalDate today = LocalDate.of(2026, 9, 3);
        EduDashboardView view = new EduDashboardView(
                EduTimeRange.TODAY, today, today, null, EduProgressSummary.zero(),
                List.of(), List.of(), List.of(), List.of(), LocalDateTime.of(2026, 9, 3, 10, 0)
        );
        when(service.dashboard(org.mockito.ArgumentMatchers.any())).thenReturn(RpcResult.success(view));
        EduDashboardController controller = new EduDashboardController();
        ReflectionTestUtils.setField(controller, "eduDashboardService", service);

        controller.dashboard(EduTimeRange.TODAY, List.of("lab-1", "lab-2"));

        verify(service).dashboard(argThat(query ->
                query.range() == EduTimeRange.TODAY
                        && query.laboratoryIds().equals(List.of("lab-1", "lab-2"))
        ));
    }
}
