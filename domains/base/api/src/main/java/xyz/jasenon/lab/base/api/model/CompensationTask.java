package xyz.jasenon.lab.base.api.model;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.jasenon.lab.persistence.model.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "compensation_task", autoResultMap = true)
public class CompensationTask extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    private String code;
    private String handlerType;
    private String cronExpression;
    private String zoneId;
    private MisfirePolicy misfirePolicy;
    private Boolean enabled;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payload;
    private LocalDateTime nextFireAt;
    private LocalDateTime lastScheduledAt;
    private LocalDateTime lastStartedAt;
    private LocalDateTime lastFinishedAt;
    private String lastStatus;
    private String lastError;
    private String lockedBy;
    private LocalDateTime lockUntil;

    public enum MisfirePolicy { FIRE_ONCE, SKIP }
}
