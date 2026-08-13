package xyz.jasenon.lab.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.auth.annotation.ActionAuthorized;
import xyz.jasenon.lab.audit.api.model.AuditLogQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogPage;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogView;
import xyz.jasenon.lab.audit.api.service.AuditLogService;
import xyz.jasenon.lab.audit.persistence.AuditLogEntity;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;
import xyz.jasenon.lab.common.rpc.RpcResult;

import java.util.List;

@DubboService
public class AuditLogServiceImpl implements AuditLogService {

    private static final long DEFAULT_PAGE_SIZE = 20;
    private static final long MAX_PAGE_SIZE = 100;

    private final AuditLogMapper mapper;

    public AuditLogServiceImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public RpcResult<List<AuditLogView>> query(AuditLogQuery query) {
        AuditLogQuery safeQuery = query == null ? new AuditLogQuery(null, null, null, 100) : query;
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(hasText(safeQuery.subjectId()), AuditLogEntity::getSubjectId, safeQuery.subjectId())
                .like(hasText(safeQuery.action()), AuditLogEntity::getActions, safeQuery.action())
                .like(hasText(safeQuery.objectType()), AuditLogEntity::getObjectTypes, safeQuery.objectType())
                .orderByDesc(AuditLogEntity::getOccurredAt)
                .last("LIMIT " + safeQuery.limit());
        return RpcResult.success(
                mapper.selectList(wrapper).stream().map(AuditLogServiceImpl::toView).toList()
        );
    }

    @Override
    @ActionAuthorized
    public RpcResult<AuditLogPage> page(AuditLogPageQuery query) {
        AuditLogPageQuery safeQuery = query == null
                ? new AuditLogPageQuery(1, DEFAULT_PAGE_SIZE, null, null, null, null, null, null,
                null, null, null, null, null, null, null)
                : query;
        long current = normalizeCurrent(safeQuery.current());
        long size = normalizeSize(safeQuery.size());
        Page<AuditLogEntity> result = mapper.selectPage(Page.of(current, size), pageWrapper(safeQuery));
        return RpcResult.success(new AuditLogPage(
                result.getRecords().stream().map(AuditLogServiceImpl::toView).toList(),
                result.getTotal(), result.getCurrent(), result.getSize(), result.getPages()
        ));
    }

    static LambdaQueryWrapper<AuditLogEntity> pageWrapper(AuditLogPageQuery query) {
        return new LambdaQueryWrapper<AuditLogEntity>()
                .eq(hasText(query.subjectId()), AuditLogEntity::getSubjectId, trim(query.subjectId()))
                .like(hasText(query.subjectName()), AuditLogEntity::getSubjectName, trim(query.subjectName()))
                .like(hasText(query.subjectDisplayName()), AuditLogEntity::getSubjectDisplayName, trim(query.subjectDisplayName()))
                .eq(hasText(query.operation()), AuditLogEntity::getOperation, trim(query.operation()))
                .like(hasText(query.action()), AuditLogEntity::getActions, trim(query.action()))
                .like(hasText(query.objectType()), AuditLogEntity::getObjectTypes, trim(query.objectType()))
                .like(hasText(query.objectId()), AuditLogEntity::getObjectIds, trim(query.objectId()))
                .like(hasText(query.eventType()), AuditLogEntity::getEventTypes, trim(query.eventType()))
                .like(hasText(query.description()), AuditLogEntity::getDescription, trim(query.description()))
                .eq(hasText(query.traceId()), AuditLogEntity::getTraceId, trim(query.traceId()))
                .eq(hasText(query.requestId()), AuditLogEntity::getRequestId, trim(query.requestId()))
                .ge(query.occurredFrom() != null, AuditLogEntity::getOccurredAt, query.occurredFrom())
                .le(query.occurredTo() != null, AuditLogEntity::getOccurredAt, query.occurredTo())
                .orderByDesc(AuditLogEntity::getOccurredAt)
                .orderByDesc(AuditLogEntity::getId);
    }

    static long normalizeCurrent(long current) {
        return current < 1 ? 1 : current;
    }

    static long normalizeSize(long size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    private static AuditLogView toView(AuditLogEntity entity) {
        return new AuditLogView(
                entity.getId(), entity.getSubjectId(), entity.getSubjectName(), entity.getSubjectDisplayName(),
                entity.getOperation(), entity.getActions(), entity.getObjectTypes(), entity.getObjectIds(), entity.getEventTypes(),
                entity.getDescription(), entity.getTraceId(), entity.getRequestId(), entity.getOccurredAt()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
