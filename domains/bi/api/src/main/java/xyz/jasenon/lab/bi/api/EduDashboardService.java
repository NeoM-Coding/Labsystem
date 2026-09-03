package xyz.jasenon.lab.bi.api;

import xyz.jasenon.lab.bi.api.query.EduDashboardQuery;
import xyz.jasenon.lab.bi.api.view.EduDashboardView;
import xyz.jasenon.lab.common.rpc.RpcResult;

public interface EduDashboardService {

    RpcResult<EduDashboardView> dashboard(EduDashboardQuery query);
}
