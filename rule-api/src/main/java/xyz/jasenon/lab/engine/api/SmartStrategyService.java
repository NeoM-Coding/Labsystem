package xyz.jasenon.lab.engine.api;

import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyDelete;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyListQuery;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.api.command.SmartStrategyUpdate;

import java.util.List;

public interface SmartStrategyService {

    RuntimeRevision create(SmartStrategyCreate command);

    RuntimeRevision update(SmartStrategyUpdate command);

    void delete(SmartStrategyDelete command);

    RuntimeRevision changeStatus(SmartStrategyStatusChange command);

    RuntimeRevision get(SmartStrategyGet command);

    List<RuntimeRevision> list(SmartStrategyListQuery command);
}
