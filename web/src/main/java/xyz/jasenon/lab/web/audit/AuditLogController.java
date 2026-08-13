package xyz.jasenon.lab.web.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.jasenon.lab.audit.api.model.AuditLogPage;
import xyz.jasenon.lab.audit.api.model.AuditLogPageQuery;
import xyz.jasenon.lab.audit.api.service.AuditLogService;
import xyz.jasenon.lab.common.util.R;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.rpc.RpcClient;
import xyz.jasenon.lab.web.response.DiyResponseEntity;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/audit-logs")
@Traced("audit-log-web")
@Tag(name = "审计日志", description = "分页查询管理员业务操作审计日志")
public class AuditLogController {

    @DubboReference(check = false)
    private AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "分页查询审计日志", description = "支持按操作人、操作、资源、链路标识和发生时间筛选。")
    public DiyResponseEntity<R<AuditLogPage>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String subjectDisplayName,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredTo
    ) {
        AuditLogPageQuery query = new AuditLogPageQuery(
                current, size, subjectId, subjectName, subjectDisplayName, operation, action,
                objectType, objectId, eventType, description, traceId, requestId, occurredFrom, occurredTo
        );
        return DiyResponseEntity.of(R.success(RpcClient.call(() -> auditLogService.page(query))));
    }
}
