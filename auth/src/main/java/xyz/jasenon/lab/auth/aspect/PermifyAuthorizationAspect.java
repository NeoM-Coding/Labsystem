package xyz.jasenon.lab.auth.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import xyz.jasenon.lab.auth.SourceType;
import xyz.jasenon.lab.auth.annotation.Mode;
import xyz.jasenon.lab.auth.annotation.PostAuth;
import xyz.jasenon.lab.auth.annotation.PreAuth;
import xyz.jasenon.lab.auth.client.AuthorizationOperations;
import xyz.jasenon.lab.auth.context.UserContext;
import xyz.jasenon.lab.auth.context.UserContextHolder;
import xyz.jasenon.lab.auth.exception.AuthenticationRequiredException;
import xyz.jasenon.lab.auth.exception.AuthorizationConfigurationException;
import xyz.jasenon.lab.auth.exception.PermissionDeniedException;
import xyz.jasenon.lab.auth.permission.Permission;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Order(100)
public class PermifyAuthorizationAspect {

    private final AuthorizationOperations authorizationOperations;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public PermifyAuthorizationAspect(AuthorizationOperations authorizationOperations) {
        this.authorizationOperations = authorizationOperations;
    }

    @Around("@annotation(xyz.jasenon.lab.auth.annotation.PreAuth) "
            + "|| @annotation(xyz.jasenon.lab.auth.annotation.PostAuth)")
    public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        PreAuth preAuth = AnnotatedElementUtils.findMergedAnnotation(method, PreAuth.class);
        PostAuth postAuth = AnnotatedElementUtils.findMergedAnnotation(method, PostAuth.class);

        if (preAuth != null) {
            authorize(method, joinPoint.getArgs(), AuthorizationDefinition.from(preAuth));
        }

        Object result = joinPoint.proceed();

        if (postAuth != null) {
            authorize(method, joinPoint.getArgs(), AuthorizationDefinition.from(postAuth));
        }
        return result;
    }

    private void authorize(Method method, Object[] arguments, AuthorizationDefinition definition) {
        validateDefinition(method, definition);
        UserContext context = UserContextHolder.get();
        if (context == null || isBlank(context.getUserId())) {
            throw new AuthenticationRequiredException("当前请求缺少有效的 UserContext");
        }

        String entityId = resolveEntityId(method, arguments, definition);
        String permissionName = definition.permission().trim();
        Permission permission = () -> permissionName;
        boolean allowed = authorizationOperations.check(
                definition.entityType(),
                entityId,
                permission,
                SourceType.user,
                context.getUserId().trim()
        );
        if (!allowed) {
            throw new PermissionDeniedException(definition.entityType(), entityId, permissionName);
        }
    }

    private String resolveEntityId(Method method, Object[] arguments, AuthorizationDefinition definition) {
        if (definition.idMode() == Mode.Constant) {
            return requireText(definition.entityId(), method, "entityId");
        }

        String expressionText = requireText(definition.entityId(), method, "entityId SpEL");
        try {
            Expression expression = expressionCache.computeIfAbsent(
                    expressionText,
                    expressionParser::parseExpression
            );
            MethodBasedEvaluationContext evaluationContext = new MethodBasedEvaluationContext(
                    null,
                    method,
                    arguments,
                    parameterNameDiscoverer
            );
            Object value = expression.getValue(evaluationContext);
            return requireText(value == null ? null : String.valueOf(value), method, "entityId SpEL result");
        } catch (AuthorizationConfigurationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationConfigurationException(
                    "无法解析 " + method.toGenericString() + " 的 entityId SpEL: " + expressionText,
                    exception
            );
        }
    }

    private static void validateDefinition(Method method, AuthorizationDefinition definition) {
        if (definition.entityType() == SourceType.none) {
            throw new AuthorizationConfigurationException(
                    method.toGenericString() + " 的 entityType 不能为 none"
            );
        }
        requireText(definition.permission(), method, "permission");
    }

    private static String requireText(String value, Method method, String field) {
        if (isBlank(value)) {
            throw new AuthorizationConfigurationException(
                    method.toGenericString() + " 的 " + field + " 不能为空"
            );
        }
        return value.trim();
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        return target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record AuthorizationDefinition(
            SourceType entityType,
            String entityId,
            Mode idMode,
            String permission
    ) {

        private static AuthorizationDefinition from(PreAuth annotation) {
            return new AuthorizationDefinition(
                    annotation.entityType(),
                    annotation.entityId(),
                    annotation.idMode(),
                    annotation.permission()
            );
        }

        private static AuthorizationDefinition from(PostAuth annotation) {
            return new AuthorizationDefinition(
                    annotation.entityType(),
                    annotation.entityId(),
                    annotation.idMode(),
                    annotation.permission()
            );
        }
    }
}
