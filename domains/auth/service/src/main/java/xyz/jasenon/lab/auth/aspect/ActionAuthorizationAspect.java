package xyz.jasenon.lab.auth.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import xyz.jasenon.lab.auth.command.ActionCommand;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.handler.ActionCommandHandlerRegistry;
import xyz.jasenon.lab.auth.service.Auth;

import java.util.Arrays;
import java.util.List;

@Aspect
@Order(90)
public class ActionAuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(ActionAuthorizationAspect.class);

    private final ActionCommandHandlerRegistry registry;
    private final Auth auth;

    public ActionAuthorizationAspect(ActionCommandHandlerRegistry registry, Auth auth) {
        this.registry = registry;
        this.auth = auth;
    }

    @Around("@annotation(xyz.jasenon.lab.auth.annotation.ActionAuthorized)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        UserContext context = UserContextHolder.get();
        if (context == null || context.getUserId() == null || context.getUserId().isBlank()) {
            throw new AuthenticationRequiredException("当前请求缺少有效的 UserContext");
        }

        // 多参数方法可能产生多个授权目标，必须全部通过后才允许进入业务方法。
        List<ActionCommand> commands = Arrays.stream(joinPoint.getArgs())
                .map(argument -> registry.handle(argument, context))
                .flatMap(java.util.Optional::stream)
                .toList();
        if (commands.isEmpty()) {
            log.warn("authorization_handler_missing method={} argument_types={} action=allow",
                    joinPoint.getSignature().toShortString(), argumentTypes(joinPoint.getArgs()));
            return joinPoint.proceed();
        }

        for (ActionCommand command : commands) {
            if (!auth.check(command)) {
                throw new PermissionDeniedException(
                        command.entityType(), command.entityId(), command.action().str()
                );
            }
        }
        return joinPoint.proceed();
    }

    private static List<String> argumentTypes(Object[] arguments) {
        if (arguments == null || arguments.length == 0) return List.of();
        return Arrays.stream(arguments)
                .map(argument -> argument == null ? "null" : argument.getClass().getName())
                .toList();
    }
}
