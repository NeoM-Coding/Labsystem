package xyz.jasenon.lab.bi.api;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import xyz.jasenon.lab.common.rpc.RpcResult;
public interface BiMaintenanceService {
    RpcResult<List<Map<String, Object>>> status();
    RpcResult<String> backfill(LocalDate start, LocalDate end);
    RpcResult<String> archive(boolean dryRun);
    RpcResult<String> retry(String jobId);
}
