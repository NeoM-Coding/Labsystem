package xyz.jasenon.lab.observability.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.observability.annotation.Traced;
import xyz.jasenon.lab.observability.context.TraceContext;
import xyz.jasenon.lab.observability.log.SafeArgumentRenderer;

import java.lang.reflect.Method;

@Aspect
public class TracedAspect {

    private static final Logger log = LoggerFactory.getLogger(TracedAspect.class);
    private final SafeArgumentRenderer renderer;

    public TracedAspect(SafeArgumentRenderer renderer) {
        this.renderer = renderer;
    }

    @Around("@annotation(xyz.jasenon.lab.observability.annotation.Traced) || "
            + "@within(xyz.jasenon.lab.observability.annotation.Traced)")
    public Object trace(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        Traced traced = AnnotatedElementUtils.findMergedAnnotation(method, Traced.class);
        if (traced == null) {
            traced = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Traced.class);
        }
        if (traced == null) {
            return joinPoint.proceed();
        }

        boolean ownsContext = TraceContext.traceId() == null;
        TraceContext.Scope scope = ownsContext ? TraceContext.open(null, null) : null;
        addUserToMdc();
        String operation = traced.value().isBlank()
                ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
                : traced.value();
        long startedAt = System.nanoTime();
        String arguments = traced.recordArgs() ? renderer.render(joinPoint.getArgs()) : "<disabled>";
        try {
            log.info("trace_start operation={} args={}", operation, arguments);
            Object result = joinPoint.proceed();
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (traced.recordResult()) {
                log.info("trace_success operation={} duration_ms={} result={}", operation, elapsedMs, renderer.render(result));
            } else {
                log.info("trace_success operation={} duration_ms={}", operation, elapsedMs);
            }
            return result;
        } catch (Throwable error) {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            log.error("trace_failure operation={} duration_ms={} error_type={} error_message={}",
                    operation, elapsedMs, error.getClass().getName(), error.getMessage(), error);
            throw error;
        } finally {
            MDC.remove(TraceContext.USER_ID);
            MDC.remove(TraceContext.USERNAME);
            if (scope != null) scope.close();
        }
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
    }

    private static void addUserToMdc() {
        UserContext user = UserContextHolder.get();
        if (user != null) {
            TraceContext.putUser(user.getUserId(), user.getUsername());
        }
    }
}
