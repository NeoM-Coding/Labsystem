package xyz.jasenon.lab.web.contract;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.MqttDeviceCRUD;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.MqttPollCo;
import xyz.jasenon.lab.api.mqtt.MqttRuleIo;
import xyz.jasenon.lab.api.mqtt.MqttTelemetryQuery;
import xyz.jasenon.lab.audit.api.service.AuditLogService;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.common.rpc.RpcResult;
import xyz.jasenon.lab.engine.api.SmartStrategyService;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class DubboSerializationContractTests {

    private static final String PROJECT_PACKAGE_PREFIX = "xyz.jasenon.lab";

    private static final Set<Class<?>> DUBBO_CONTRACTS = Set.of(
            UserService.class,
            LaboratoryService.class,
            MqttIo.class,
            MqttRuleIo.class,
            MqttPollCo.class,
            MqttDeviceCRUD.class,
            MqttGatewayCRUD.class,
            MqttTelemetryQuery.class,
            SmartStrategyService.class,
            AuditLogService.class
    );

    @Test
    void allDubboMethodsReturnRpcResultInsteadOfTransportingExceptions() {
        DUBBO_CONTRACTS.forEach(contract ->
                java.util.Arrays.stream(contract.getMethods()).forEach(method ->
                        assertThat(isRpcResult(method.getGenericReturnType()))
                                .as("%s.%s must return RpcResult<T> or CompletableFuture<RpcResult<T>>",
                                        contract.getSimpleName(), method.getName())
                                .isTrue()
                ));
    }

    @Test
    void allDubboPayloadTypesAreSerializable() {
        Set<Type> inspected = new HashSet<>();

        DUBBO_CONTRACTS.forEach(contract ->
                java.util.Arrays.stream(contract.getMethods()).forEach(method -> {
                    inspect(method.getGenericReturnType(), contract.getSimpleName() + "." + method.getName(), inspected);
                    for (Type parameter : method.getGenericParameterTypes()) {
                        inspect(parameter, contract.getSimpleName() + "." + method.getName(), inspected);
                    }
                }));
    }

    @Test
    void projectRpcPayloadsAreCoveredByDubboStrictAllowlist() throws Exception {
        var resources = Thread.currentThread().getContextClassLoader()
                .getResources("security/serialize.allowlist");
        List<URL> allowlists = Collections.list(resources);

        assertThat(allowlists)
                .as("common must publish Dubbo's security/serialize.allowlist resource")
                .isNotEmpty();

        List<String> prefixes = new ArrayList<>();
        for (URL allowlist : allowlists) {
            try (var input = allowlist.openStream()) {
                new String(input.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(prefixes::add);
            }
        }

        assertThat(prefixes)
                .as("Dubbo STRICT mode must trust project-owned payloads and their subclasses")
                .anyMatch(PROJECT_PACKAGE_PREFIX::startsWith);
    }

    private static void inspect(Type type, String location, Set<Type> inspected) {
        if (type == null || !inspected.add(type)) {
            return;
        }
        if (type instanceof ParameterizedType parameterized) {
            inspect(parameterized.getRawType(), location, inspected);
            for (Type argument : parameterized.getActualTypeArguments()) {
                inspect(argument, location, inspected);
            }
            return;
        }
        if (type instanceof GenericArrayType array) {
            inspect(array.getGenericComponentType(), location, inspected);
            return;
        }
        if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) inspect(bound, location, inspected);
            return;
        }
        if (type instanceof TypeVariable<?>) {
            return;
        }
        if (!(type instanceof Class<?> payload) || isIntrinsic(payload)) {
            return;
        }
        if (payload.isArray()) {
            inspect(payload.getComponentType(), location, inspected);
            return;
        }

        assertThat(Serializable.class.isAssignableFrom(payload))
                .as("Dubbo payload %s used by %s must implement Serializable", payload.getName(), location)
                .isTrue();

        inspectPolymorphicSubtypes(payload, location, inspected);
        inspectFields(payload, location, inspected);
    }

    private static void inspectPolymorphicSubtypes(Class<?> payload, String location, Set<Type> inspected) {
        JsonSubTypes jsonSubTypes = payload.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes != null) {
            for (JsonSubTypes.Type subtype : jsonSubTypes.value()) {
                inspect(subtype.value(), location + " -> subtype of " + payload.getSimpleName(), inspected);
            }
        }
        if (payload.isSealed()) {
            for (Class<?> subtype : payload.getPermittedSubclasses()) {
                inspect(subtype, location + " -> subtype of " + payload.getSimpleName(), inspected);
            }
        }
    }

    private static void inspectFields(Class<?> payload, String location, Set<Type> inspected) {
        for (Class<?> current = payload; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    inspect(field.getGenericType(), location + " -> " + payload.getSimpleName() + "." + field.getName(), inspected);
                }
            }
        }
    }

    private static boolean isIntrinsic(Class<?> type) {
        return type == void.class
                || type.isPrimitive()
                || type.isEnum()
                || type == Object.class
                || type.getPackageName().startsWith("java.");
    }

    private static boolean isRpcResult(Type type) {
        if (!(type instanceof ParameterizedType parameterized)
                || !(parameterized.getRawType() instanceof Class<?> rawType)) {
            return false;
        }
        if (rawType == RpcResult.class) {
            return true;
        }
        if (rawType != CompletableFuture.class) {
            return false;
        }
        Type[] arguments = parameterized.getActualTypeArguments();
        return arguments.length == 1 && isRpcResult(arguments[0]);
    }
}
