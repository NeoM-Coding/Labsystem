package xyz.jasenon.lab.web.contract;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.MqttDeviceCRUD;
import xyz.jasenon.lab.api.mqtt.MqttGatewayCRUD;
import xyz.jasenon.lab.api.mqtt.MqttIo;
import xyz.jasenon.lab.api.mqtt.MqttPollCo;
import xyz.jasenon.lab.audit.api.service.AuditLogService;
import xyz.jasenon.lab.base.api.service.LaboratoryService;
import xyz.jasenon.lab.base.api.service.UserService;
import xyz.jasenon.lab.engine.api.SmartStrategyService;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DubboSerializationContractTests {

    private static final Set<Class<?>> DUBBO_CONTRACTS = Set.of(
            UserService.class,
            LaboratoryService.class,
            MqttIo.class,
            MqttPollCo.class,
            MqttDeviceCRUD.class,
            MqttGatewayCRUD.class,
            SmartStrategyService.class,
            AuditLogService.class
    );

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

        inspectFields(payload, location, inspected);
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
}
