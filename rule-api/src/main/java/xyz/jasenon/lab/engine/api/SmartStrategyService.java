package xyz.jasenon.lab.engine.api;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyDelete;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyListQuery;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.api.command.SmartStrategyUpdate;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

public interface SmartStrategyService {

    RpcResult<RuntimeRevision> create(SmartStrategyCreate command);

    RpcResult<RuntimeRevision> update(SmartStrategyUpdate command);

    RpcResult<Void> delete(SmartStrategyDelete command);

    RpcResult<RuntimeRevision> changeStatus(SmartStrategyStatusChange command);

    RpcResult<RuntimeRevision> get(SmartStrategyGet command);

    RpcResult<List<RuntimeRevision>> list(SmartStrategyListQuery command);
}
