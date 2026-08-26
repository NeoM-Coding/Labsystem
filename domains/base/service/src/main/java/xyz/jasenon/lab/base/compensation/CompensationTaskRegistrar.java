package xyz.jasenon.lab.base.compensation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import xyz.jasenon.lab.base.api.model.CompensationTask;
import xyz.jasenon.lab.base.mapper.CompensationTaskMapper;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "lab.compensation.registration-enabled", havingValue = "true", matchIfMissing = true)
public class CompensationTaskRegistrar {
    private final CompensationTaskMapper mapper;
    private final List<CompensationTaskHandler> handlers;

    public CompensationTaskRegistrar(CompensationTaskMapper mapper,
                                     List<CompensationTaskHandler> handlers) {
        this.mapper = mapper;
        this.handlers = handlers;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerMissingTasks() {
        for (CompensationTaskHandler handler : handlers) {
            Class<?> handlerClass = AopUtils.getTargetClass(handler);
            CompensationJob job = AnnotationUtils.findAnnotation(handlerClass, CompensationJob.class);
            if (job == null) {
                throw new IllegalStateException("补偿处理器缺少 @CompensationJob: " + handlerClass.getName());
            }
            if (!job.code().equals(handler.type())) {
                throw new IllegalStateException("补偿任务 code 与 handler type 不一致: " + job.code());
            }
            Long count = mapper.selectCount(new LambdaQueryWrapper<CompensationTask>()
                    .eq(CompensationTask::getCode, job.code()));
            if (count != null && count > 0) continue;

            CompensationTask task = new CompensationTask();
            task.setCode(job.code());
            task.setHandlerType(handler.type());
            task.setCronExpression(job.cron());
            task.setZoneId(job.zoneId());
            task.setMisfirePolicy(job.misfire());
            task.setEnabled(job.enabled());
            task.setNextFireAt(CompensationTaskScheduler.nextFire(task, LocalDateTime.now()));
            try {
                mapper.insert(task);
            } catch (DuplicateKeyException ignored) {
                // 多实例首次同时启动时，由唯一索引保证只有一个注册成功。
            }
        }
    }
}
