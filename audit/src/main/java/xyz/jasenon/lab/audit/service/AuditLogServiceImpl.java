package xyz.jasenon.lab.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.audit.api.model.AuditLogQuery;
import xyz.jasenon.lab.audit.api.model.AuditLogView;
import xyz.jasenon.lab.audit.api.service.AuditLogService;
import xyz.jasenon.lab.audit.persistence.AuditLogEntity;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;

import java.util.List;

@DubboService
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper mapper;

    public AuditLogServiceImpl(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AuditLogView> query(AuditLogQuery query) {
        AuditLogQuery safeQuery = query == null ? new AuditLogQuery(null, null, null, 100) : query;
        LambdaQueryWrapper<AuditLogEntity> wrapper = new LambdaQueryWrapper<AuditLogEntity>()
                .eq(hasText(safeQuery.subjectId()), AuditLogEntity::getSubjectId, safeQuery.subjectId())
                .like(hasText(safeQuery.action()), AuditLogEntity::getActions, safeQuery.action())
                .like(hasText(safeQuery.objectType()), AuditLogEntity::getObjectTypes, safeQuery.objectType())
                .orderByDesc(AuditLogEntity::getOccurredAt)
                .last("LIMIT " + safeQuery.limit());
        return mapper.selectList(wrapper).stream().map(AuditLogServiceImpl::toView).toList();
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
}
