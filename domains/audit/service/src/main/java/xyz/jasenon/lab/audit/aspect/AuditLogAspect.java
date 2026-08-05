package xyz.jasenon.lab.audit.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.audit.api.annotation.Audited;
import xyz.jasenon.lab.audit.handler.AuditFragment;
import xyz.jasenon.lab.audit.handler.AuditHandlerRegistry;
import xyz.jasenon.lab.audit.model.AuditEvent;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.observability.context.TraceContext;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Aspect
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final AuditHandlerRegistry registry;
    private final AuditLogStore store;

    public AuditLogAspect(AuditHandlerRegistry registry, AuditLogStore store) {
        this.registry = registry;
        this.store = store;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        // 只记录已经成功完成的管理员操作，失败调用由运维追踪日志负责。
        Object result = joinPoint.proceed();
        UserContext subject = UserContextHolder.get();
        if (subject == null || subject.getUserId() == null || subject.getUserId().isBlank()) {
            log.warn("audit_skipped reason=missing_user_context method={}", joinPoint.getSignature().toShortString());
            return result;
        }

        List<AuditFragment> fragments = Arrays.stream(joinPoint.getArgs())
                .map(argument -> registry.handle(argument, result))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (fragments.isEmpty()) return result;

        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String operation = audited.value().isBlank()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : audited.value();
        try {
            store.append(new AuditEvent(
                    subject.getUserId(),
                    subject.getUsername(),
                    subject.getDisplayName(),
                    operation,
                    fragments,
                    TraceContext.traceId(),
                    TraceContext.requestId(),
                    LocalDateTime.now()
            ));
        } catch (RuntimeException error) {
            // 审计存储故障不能回滚已经成功的核心业务。
            log.error("audit_persist_failure operation={} subject_id={}", operation, subject.getUserId(), error);
        }
        return result;
    }
}
