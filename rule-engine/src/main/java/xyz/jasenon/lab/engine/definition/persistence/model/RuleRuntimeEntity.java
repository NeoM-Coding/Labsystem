package xyz.jasenon.lab.engine.definition.persistence.model;

import com.baomidou.mybatisplus.annotation.TableName;
import xyz.jasenon.lab.common.model.BaseEntity;

import java.time.Instant;

/**
 * Runtime 可检索元数据。完整规则内容保存在不可变 revision 表中。
 */
@TableName("rule_runtime")
public class RuleRuntimeEntity extends BaseEntity {

    private String runtimeId;
    private String runtimeName;
    private String ownerId;
    private Boolean enabled;
    private String status;
    private Integer publishedRevisionNo;
    private Instant activeFrom;
    private Instant activeUntil;

    public RuleRuntimeEntity() {
    }

    public RuleRuntimeEntity(
            String runtimeId,
            String runtimeName,
            String ownerId,
            Boolean enabled,
            String status,
            Integer publishedRevisionNo,
            Instant activeFrom,
            Instant activeUntil
    ) {
        this.runtimeId = runtimeId;
        this.runtimeName = runtimeName;
        this.ownerId = ownerId;
        this.enabled = enabled;
        this.status = status;
        this.publishedRevisionNo = publishedRevisionNo;
        this.activeFrom = activeFrom;
        this.activeUntil = activeUntil;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public String getRuntimeName() {
        return runtimeName;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public String getStatus() {
        return status;
    }

    public Integer getPublishedRevisionNo() {
        return publishedRevisionNo;
    }

    public Instant getActiveFrom() {
        return activeFrom;
    }

    public Instant getActiveUntil() {
        return activeUntil;
    }
}
