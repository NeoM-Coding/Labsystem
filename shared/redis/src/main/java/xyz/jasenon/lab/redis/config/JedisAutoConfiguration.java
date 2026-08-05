package xyz.jasenon.lab.redis.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import xyz.jasenon.lab.redis.core.RedisBus;

import java.time.Duration;

@AutoConfiguration
@EnableConfigurationProperties(RedisOptions.class)
@ConditionalOnProperty(prefix = "lab.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JedisAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public JedisPool jedisPool(RedisOptions options) {
        RedisOptions.Pool pool = options.getPool();
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(pool.getMaxTotal());
        poolConfig.setMaxIdle(pool.getMaxIdle());
        poolConfig.setMinIdle(pool.getMinIdle());
        poolConfig.setMaxWait(Duration.ofMillis(pool.getMaxWaitMillis()));
        poolConfig.setTestOnBorrow(pool.isTestOnBorrow());
        poolConfig.setJmxEnabled(false);

        String username = options.getUsername().isBlank() ? null : options.getUsername();
        String password = options.getPassword().isBlank() ? null : options.getPassword();
        return new JedisPool(
                poolConfig,
                options.getHost(),
                options.getPort(),
                options.getTimeoutMillis(),
                username,
                password,
                options.getDatabase()
        );
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public RedisBus redisBus(JedisPool jedisPool, RedisOptions options) {
        return new RedisBus(jedisPool, options.getKeyPrefix());
    }
}
