package xyz.jasenon.lab.web.bi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;
import xyz.jasenon.lab.bi.api.BiMaintenanceService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;
import java.time.LocalDate;
import java.util.*;
@RestController
@RequestMapping("/api/bi/maintenance")
public class BiMaintenanceController {
    @DubboReference(check=false) private BiMaintenanceService maintenanceService;
    public record Backfill(LocalDate startDate,LocalDate endDate) {}
    @GetMapping public DiyResponseEntity<R<List<Map<String,Object>>>> status() { return DiyResponseEntity.of(R.success(RpcClient.call(()->maintenanceService.status()))); }
    @PostMapping("/backfill") public DiyResponseEntity<R<String>> backfill(@RequestBody Backfill input) {
        return DiyResponseEntity.of(R.success(RpcClient.call(()->maintenanceService.backfill(input.startDate(),input.endDate()))));
    }
    @PostMapping("/{jobId}/retry") public DiyResponseEntity<R<String>> retry(@PathVariable String jobId) {
        return DiyResponseEntity.of(R.success(RpcClient.call(()->maintenanceService.retry(jobId))));
    }
    @PostMapping("/archive") public DiyResponseEntity<R<String>> archive(@RequestParam(defaultValue="true") boolean dryRun) {
        return DiyResponseEntity.of(R.success(RpcClient.call(()->maintenanceService.archive(dryRun))));
    }
}
