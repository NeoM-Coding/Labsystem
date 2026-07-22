package xyz.jasenon.lab.auth.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.util.Optional;

public class RedisUserContextStore implements UserContextStore {

    private static final String KEY_PREFIX = "auth:user-context:";

    private final RedisBus redis;
    private final ObjectMapper objectMapper;

    public RedisUserContextStore(RedisBus redis) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // UserContext exposes computed getters for filtering; they are not persisted state.
                .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                // Existing Redis snapshots may still contain old computed properties.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public void save(UserContext context) {
        if (context == null || !hasText(context.getUserId())) {
            throw new IllegalArgumentException("UserContext.userId 不能为空");
        }
        try {
            // UserContext 是登录会话的权限快照，按项目约定不设置 TTL，由权限变更或注销显式刷新。
            redis.set(key(context.getUserId()), objectMapper.writeValueAsString(context));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserContext 序列化失败", e);
        }
    }

    @Override
    public Optional<UserContext> find(String userId) {
        if (!hasText(userId)) return Optional.empty();
        String json = redis.get(key(userId));
        if (!hasText(json)) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, UserContext.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("UserContext 反序列化失败", e);
        }
    }

    @Override
    public void delete(String userId) {
        if (hasText(userId)) redis.delete(key(userId));
    }

    private static String key(String userId) {
        return KEY_PREFIX + userId.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
