package xyz.jasenon.lab.engine.api;

import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.engine.api.command.AlertLogListQuery;
import xyz.jasenon.lab.engine.api.model.AlertLogPage;

public interface RuleAlertLogService {

    RpcResult<AlertLogPage> list(AlertLogListQuery query);
}
