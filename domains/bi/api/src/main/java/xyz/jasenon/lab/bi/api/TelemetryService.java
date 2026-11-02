package xyz.jasenon.lab.bi.api;
import xyz.jasenon.lab.bi.api.query.TelemetryQuery;
import xyz.jasenon.lab.bi.api.view.TelemetryDashboard;
import xyz.jasenon.lab.common.rpc.RpcResult;
public interface TelemetryService {
    RpcResult<TelemetryDashboard> dashboard(TelemetryQuery query);
    RpcResult<xyz.jasenon.lab.bi.api.view.TelemetryFilterOptions> filterOptions(TelemetryQuery query);
}
