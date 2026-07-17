package xyz.jasenon.lab.engine.definition.persistence.model;

import com.baomidou.mybatisplus.annotation.TableName;
import xyz.jasenon.lab.persistence.model.BaseEntity;

/**
 * Runtime 的不可变 JSON revision。
 */
@TableName("rule_runtime_revision")
public class RuleRuntimeRevisionEntity extends BaseEntity {

    private String runtimeId;
    private Integer revisionNo;
    private Integer schemaVersion;
    private String definition;
    private String checksum;
    private String createdBy;

    public RuleRuntimeRevisionEntity() {
    }

    public RuleRuntimeRevisionEntity(
            String runtimeId,
            Integer revisionNo,
            Integer schemaVersion,
            String definition,
            String checksum,
            String createdBy
    ) {
        this.runtimeId = runtimeId;
        this.revisionNo = revisionNo;
        this.schemaVersion = schemaVersion;
        this.definition = definition;
        this.checksum = checksum;
        this.createdBy = createdBy;
    }

    public String getRuntimeId() {
        return runtimeId;
    }

    public Integer getRevisionNo() {
        return revisionNo;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public String getDefinition() {
        return definition;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
