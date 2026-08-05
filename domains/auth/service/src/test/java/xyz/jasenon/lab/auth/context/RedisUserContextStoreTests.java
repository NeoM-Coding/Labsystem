package xyz.jasenon.lab.auth.context;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.jasenon.lab.common.realtime.RealtimeChannels;

import static org.assertj.core.api.Assertions.assertThat;

class RedisUserContextStoreTests {

    @Test
    void roundTripsPersistedFieldsWithoutComputedProperties() {
        InMemoryRedisBus redis = new InMemoryRedisBus();
        RedisUserContextStore store = new RedisUserContextStore(redis);
        UserContext source = context();

        store.save(source);

        assertThat(redis.get("auth:user-context:user-1"))
                .doesNotContain("buildingNames", "orgNames");
        assertThat(store.find("user-1")).contains(source);
        assertThat(redis.published.get(RealtimeChannels.USER_CONTEXT_CHANGED)).contains("UPSERT");
    }

    @Test
    void readsLegacySnapshotContainingComputedProperties() {
        InMemoryRedisBus redis = new InMemoryRedisBus();
        redis.set("auth:user-context:user-1", """
                {
                  "userId":"user-1",
                  "username":"admin",
                  "displayName":"Administrator",
                  "laboratoryIds":["lab-1"],
                  "laboratoryScopes":[],
                  "loginAt":"2026-07-22T12:00:00",
                  "buildingNames":["16号楼"],
                  "orgNames":["计算机科学学院"]
                }
                """);

        UserContext restored = new RedisUserContextStore(redis).find("user-1").orElseThrow();

        assertThat(restored.getUserId()).isEqualTo("user-1");
        assertThat(restored.getLaboratoryIds()).containsExactly("lab-1");
    }

    private static UserContext context() {
        return UserContext.builder()
                .userId("user-1")
                .username("admin")
                .displayName("Administrator")
                .laboratoryIds(List.of("lab-1"))
                .laboratoryScopes(List.of(UserContext.LaboratoryScope.builder()
                        .laboratoryId("lab-1")
                        .buildingName("16号楼")
                        .orgName("计算机科学学院")
                        .build()))
                .loginAt(LocalDateTime.of(2026, 7, 22, 12, 0))
                .build();
    }

    private static final class InMemoryRedisBus extends RedisBus {

        private final Map<String, String> values = new HashMap<>();
        private final Map<String, String> published = new HashMap<>();

        private InMemoryRedisBus() {
            super(new JedisPool(), "");
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public String set(String key, String value) {
            values.put(key, value);
            return "OK";
        }

        @Override
        public long delete(String key) {
            return values.remove(key) == null ? 0L : 1L;
        }

        @Override
        public long publish(String channel, String message) {
            published.put(channel, message);
            return 1L;
        }
    }
}
