package xyz.jasenon.lab.redis.core;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisBus implements AutoCloseable {

    private final JedisPool jedisPool;
    private final String keyPrefix;
    private final ExecutorService subscriberExecutor;

    public RedisBus(JedisPool jedisPool, String keyPrefix) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool");
        this.keyPrefix = normalizePrefix(keyPrefix);
        this.subscriberExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("redis-bus-subscriber");
            thread.setDaemon(true);
            return thread;
        });
    }

    public String namespaced(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return keyPrefix.isBlank() ? key : keyPrefix + ":" + key;
    }

    public String get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.get(namespaced(key));
        }
    }

    public String set(String key, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.set(namespaced(key), value);
        }
    }

    public String set(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return set(key, value);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.setex(namespaced(key), ttl.toSeconds(), value);
        }
    }

    public long delete(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.del(namespaced(key));
        }
    }

    public long publish(String channel, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.publish(namespaced(channel), message);
        }
    }

    public RedisSubscription subscribe(String channel, RedisMessageHandler handler) {
        Objects.requireNonNull(handler, "handler");
        JedisPubSub subscriber = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                handler.onMessage(channel, message);
            }
        };

        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(subscriber, namespaced(channel));
            }
        }, subscriberExecutor);
        return new RedisSubscription(subscriber, task);
    }

    @Override
    public void close() {
        subscriberExecutor.shutdownNow();
    }

    public long hset(String key, String field, String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hset(namespaced(key), field, value);
        }
    }

    public long hset(String key, Map<String, String> hash) {
        if (hash == null || hash.isEmpty()) {
            return 0L;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hset(namespaced(key), hash);
        }
    }

    public long hsetex(String key, String field, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return hset(key, field, value);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            Transaction transaction = jedis.multi();
            Response<Long> response = transaction.hset(namespaced(key), field, value);
            transaction.expire(namespaced(key), seconds(ttl));
            transaction.exec();
            return response.get();
        }
    }

    public long hsetex(String key, Map<String, String> hash, Duration ttl) {
        if (hash == null || hash.isEmpty()) {
            return 0L;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return hset(key, hash);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            Transaction transaction = jedis.multi();
            Response<Long> response = transaction.hset(namespaced(key), hash);
            transaction.expire(namespaced(key), seconds(ttl));
            transaction.exec();
            return response.get();
        }
    }

    public String hget(String key, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hget(namespaced(key), field);
        }
    }

    public List<String> hmget(String key, String... fields) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hmget(namespaced(key), fields);
        }
    }

    public Map<String, String> hgetAll(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(namespaced(key));
        }
    }

    public boolean hexists(String key, String field) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hexists(namespaced(key), field);
        }
    }

    public long hdel(String key, String... fields) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hdel(namespaced(key), fields);
        }
    }

    private static long seconds(Duration ttl) {
        return Math.max(1L, ttl.toSeconds());
    }

    private static String normalizePrefix(String keyPrefix) {
        if (keyPrefix == null) {
            return "";
        }
        String trimmed = keyPrefix.trim();
        while (trimmed.endsWith(":")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
