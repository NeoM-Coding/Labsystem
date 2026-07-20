package xyz.jasenon.lab.engine.service;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.engine.api.SmartStrategyService;
import xyz.jasenon.lab.engine.api.command.SmartStrategyCreate;
import xyz.jasenon.lab.engine.api.command.SmartStrategyDelete;
import xyz.jasenon.lab.engine.api.command.SmartStrategyGet;
import xyz.jasenon.lab.engine.api.command.SmartStrategyListQuery;
import xyz.jasenon.lab.engine.api.command.SmartStrategyStatusChange;
import xyz.jasenon.lab.engine.api.command.SmartStrategyUpdate;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.persistence.itfc.RuntimePersist;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;

@DubboService
@Traced("smart-strategy-service")
@ConditionalOnProperty(
        prefix = "lab.rule-engine.persistence",
        name = "enabled",
        havingValue = "true"
)
public class SmartStrategyServiceImpl implements SmartStrategyService {

    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;

    private final RuntimePersist runtimePersist;

    public SmartStrategyServiceImpl(RuntimePersist runtimePersist) {
        this.runtimePersist = runtimePersist;
    }

    @Override
    @ActionAuthorized
    public RuntimeRevision create(SmartStrategyCreate command) {
        if (!runtimePersist.register(command.revision())) {
            throw new BusinessException(CONFLICT, "smart strategy already exists");
        }
        return required(command.revision().runtimeId());
    }

    @Override
    @ActionAuthorized
    public RuntimeRevision update(SmartStrategyUpdate command) {
        if (!runtimePersist.update(command.runtimeId(), command.revision())) {
            throw new BusinessException(NOT_FOUND, "smart strategy doesn't exist");
        }
        return required(command.runtimeId());
    }

    @Override
    @ActionAuthorized
    public void delete(SmartStrategyDelete command) {
        if (!runtimePersist.remove(command.runtimeId())) {
            throw new BusinessException(NOT_FOUND, "smart strategy doesn't exist");
        }
    }

    @Override
    @ActionAuthorized
    public RuntimeRevision changeStatus(SmartStrategyStatusChange command) {
        boolean changed = command.enabled()
                ? runtimePersist.enable(command.runtimeId())
                : runtimePersist.disable(command.runtimeId());
        if (!changed) {
            throw new BusinessException(NOT_FOUND, "smart strategy doesn't exist");
        }
        return required(command.runtimeId());
    }

    @Override
    @ActionAuthorized
    public RuntimeRevision get(SmartStrategyGet command) {
        return required(command.runtimeId());
    }

    @Override
    @ActionAuthorized
    public List<RuntimeRevision> list(SmartStrategyListQuery command) {
        return runtimePersist.fetch();
    }

    private RuntimeRevision required(String runtimeId) {
        RuntimeRevision revision = runtimePersist.get(runtimeId);
        if (revision == null) {
            throw new BusinessException(NOT_FOUND, "smart strategy doesn't exist");
        }
        return revision;
    }
}
