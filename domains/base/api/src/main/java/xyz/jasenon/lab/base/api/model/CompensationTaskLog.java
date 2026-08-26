package xyz.jasenon.lab.base.api.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.jasenon.lab.persistence.model.BaseEntity;
import java.io.Serial;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("compensation_task_log")
public class CompensationTaskLog extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    private String taskCode;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String status;
    private Integer affectedCount;
    private String message;
}
