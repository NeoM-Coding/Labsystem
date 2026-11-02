package xyz.jasenon.lab.web.bi;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.bi.api.TelemetryService;
import xyz.jasenon.lab.bi.api.query.TelemetryQuery;
import xyz.jasenon.lab.common.rpc.RpcResult;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class TelemetryControllerTests {
    @Test void forwardsKindDatesAndScopeThroughRpcContract() {
        var service=mock(TelemetryService.class);
        when(service.dashboard(any())).thenReturn(RpcResult.success(null));
        var controller=new TelemetryController();ReflectionTestUtils.setField(controller,"telemetryService",service);
        LocalDate start=LocalDate.of(2026,9,1),end=start.plusDays(2);
        controller.dashboard("energy",TelemetryQuery.Range.CUSTOM,start,end,List.of("lab"),List.of("A"),List.of("单位"),List.of("device"));
        verify(service).dashboard(argThat(q->q.kind()==TelemetryQuery.Kind.ENERGY && q.startDate().equals(start) && q.endDate().equals(end) && q.laboratoryIds().equals(List.of("lab")) && q.buildingNames().equals(List.of("A")) && q.orgNames().equals(List.of("单位")) && q.deviceIds().equals(List.of("device"))));
        controller.dashboard("air-condition",TelemetryQuery.Range.TODAY,null,null,null,null,null,null);
        verify(service).dashboard(argThat(q->q.kind()==TelemetryQuery.Kind.AIR_CONDITION && q.range()==TelemetryQuery.Range.TODAY));
    }
    @Test void bindsRepeatedOptionFiltersFromHttp() throws Exception {
        var service=mock(TelemetryService.class);
        when(service.filterOptions(any())).thenReturn(RpcResult.success(
            new xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions(List.of(),List.of(),List.of(),List.of())));
        var controller=new TelemetryController();ReflectionTestUtils.setField(controller,"telemetryService",service);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bi/air-condition/options")
            .param("buildingNames","楼 A","楼 B").param("orgNames","电气学院").param("laboratoryIds","lab-1","lab-2"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).filterOptions(argThat(q->q.kind()==TelemetryQuery.Kind.AIR_CONDITION
            && q.buildingNames().equals(List.of("楼 A","楼 B")) && q.orgNames().equals(List.of("电气学院"))
            && q.laboratoryIds().equals(List.of("lab-1","lab-2"))));
    }
}
