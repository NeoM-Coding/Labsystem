package xyz.jasenon.lab.auth.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import xyz.jasenon.lab.auth.context.RedisUserContextStore;
import xyz.jasenon.lab.auth.context.UserContextStore;
import xyz.jasenon.lab.redis.core.RedisBus;

@AutoConfiguration(afterName = "xyz.jasenon.lab.redis.config.JedisAutoConfiguration")
public class UserContextStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedisBus.class)
    @ConditionalOnMissingBean(UserContextStore.class)
    UserContextStore userContextStore(RedisBus redisBus) {
        return new RedisUserContextStore(redisBus);
    }
}
