package xyz.jasenon.lab.web.bi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import xyz.jasenon.lab.bi.api.TelemetryService;
import xyz.jasenon.lab.bi.api.query.TelemetryQuery;
import xyz.jasenon.lab.bi.api.view.TelemetryDashboard;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bi")
public class TelemetryController {
    @DubboReference(check=false) private TelemetryService telemetryService;
    @GetMapping("/{type:air-condition|energy}/dashboard")
    public DiyResponseEntity<R<TelemetryDashboard>> dashboard(@PathVariable String type,
        @RequestParam TelemetryQuery.Range range,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate,
        @RequestParam(required=false) List<String> laboratoryIds,
        @RequestParam(required=false) List<String> buildingNames,
        @RequestParam(required=false) List<String> orgNames,
        @RequestParam(required=false) List<String> deviceIds) {
        var kind=type.equals("air-condition")?TelemetryQuery.Kind.AIR_CONDITION:TelemetryQuery.Kind.ENERGY;
        return DiyResponseEntity.of(R.success(RpcClient.call(()->telemetryService.dashboard(new TelemetryQuery(kind,range,startDate,endDate,laboratoryIds,buildingNames,orgNames,deviceIds)))));
    }
    @GetMapping("/{type:air-condition|energy}/options")
    public DiyResponseEntity<R<xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions>> options(@PathVariable String type,
        @RequestParam(required=false) List<String> buildingNames,
        @RequestParam(required=false) List<String> orgNames,
        @RequestParam(required=false) List<String> laboratoryIds) {
        var kind=type.equals("air-condition")?TelemetryQuery.Kind.AIR_CONDITION:TelemetryQuery.Kind.ENERGY;
        var query=new TelemetryQuery(kind,null,null,null,laboratoryIds,buildingNames,orgNames,List.of());
        return DiyResponseEntity.of(R.success(RpcClient.call(()->telemetryService.filterOptions(query))));
    }
}
