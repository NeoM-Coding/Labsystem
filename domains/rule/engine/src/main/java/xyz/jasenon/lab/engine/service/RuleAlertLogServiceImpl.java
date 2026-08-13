package xyz.jasenon.lab.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.engine.alert.persistence.mapper.AlertLogMapper;
import xyz.jasenon.lab.engine.alert.persistence.model.AlertLogEntity;
import xyz.jasenon.lab.engine.api.RuleAlertLogService;
import xyz.jasenon.lab.engine.api.command.AlertLogListQuery;
import xyz.jasenon.lab.engine.api.model.AlertLog;
import xyz.jasenon.lab.engine.api.model.AlertLogPage;
import xyz.jasenon.lab.observability.annotation.Traced;

import java.util.List;

@DubboService
@Traced("rule-alert-log-service")
@ConditionalOnProperty(prefix = "lab.rule-engine.persistence", name = "enabled", havingValue = "true")
public class RuleAlertLogServiceImpl implements RuleAlertLogService {

    private static final long DEFAULT_SIZE = 20;
    private static final long MAX_SIZE = 100;

    private final AlertLogMapper mapper;
    private final ObjectMapper objectMapper;

    public RuleAlertLogServiceImpl(AlertLogMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @ActionAuthorized
    public RpcResult<AlertLogPage> list(AlertLogListQuery query) {
        long current = query.current() < 1 ? 1 : query.current();
        long size = query.size() < 1 ? DEFAULT_SIZE : Math.min(query.size(), MAX_SIZE);
        LambdaQueryWrapper<AlertLogEntity> wrapper = new LambdaQueryWrapper<AlertLogEntity>()
                .eq(query.runtimeId() != null && !query.runtimeId().isBlank(), AlertLogEntity::getRuntimeId, trimmed(query.runtimeId()))
                .eq(query.actionGroupId() != null && !query.actionGroupId().isBlank(), AlertLogEntity::getActionGroupId, trimmed(query.actionGroupId()))
                .eq(query.status() != null && !query.status().isBlank(), AlertLogEntity::getStatus, upper(query.status()))
                .ge(query.matchedFrom() != null, AlertLogEntity::getMatchedAt, query.matchedFrom())
                .le(query.matchedTo() != null, AlertLogEntity::getMatchedAt, query.matchedTo())
                .isNull(AlertLogEntity::getDeleteAt)
                .orderByDesc(AlertLogEntity::getMatchedAt)
                .orderByDesc(AlertLogEntity::getId);
        Page<AlertLogEntity> result = mapper.selectPage(Page.of(current, size), wrapper);
        List<AlertLog> records = result.getRecords().stream().map(this::toModel).toList();
        return RpcResult.success(new AlertLogPage(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages()));
    }

    private AlertLog toModel(AlertLogEntity entity) {
        try {
            return new AlertLog(
                    entity.getId(), entity.getEventId(), entity.getRuntimeId(), entity.getActionGroupId(),
                    entity.getDeviceConditionGroupId(), entity.getTimeConditionGroupId(),
                    entity.getMatchedAt(), entity.getCompletedAt(), entity.getStatus(), entity.getContent(),
                    objectMapper.readValue(entity.getUserIds(), new TypeReference<List<String>>() { }),
                    objectMapper.readValue(entity.getActions(), new TypeReference<List<AlertLog.ActionResult>>() { }),
                    entity.getCreateAt()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to deserialize alert log " + entity.getId(), exception);
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
