package xyz.jasenon.lab.audit.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_operation_log")
public class AuditLogEntity {

    @TableId
    private String id;
    private String subjectId;
    private String subjectName;
    private String subjectDisplayName;
    private String operation;
    private String actions;
    private String objectTypes;
    private String objectIds;
    private String eventTypes;
    private String description;
    private String traceId;
    private String requestId;
    private LocalDateTime occurredAt;
}
