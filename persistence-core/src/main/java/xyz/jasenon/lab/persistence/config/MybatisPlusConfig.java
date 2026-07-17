package xyz.jasenon.lab.persistence.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import io.github.sunjieyi60.uid.starter.UidGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 将 uid-springboot-starter 接入 MyBatis-Plus 的 ASSIGN_ID 策略。
 */
@AutoConfiguration
public class MybatisPlusConfig {

    @Bean
    public CustomIdGenerator customIdGenerator(UidGenerator uidGenerator) {
        return new CustomIdGenerator(uidGenerator);
    }

    public static class CustomIdGenerator implements IdentifierGenerator {

        private final UidGenerator generator;

        public CustomIdGenerator(UidGenerator generator) {
            this.generator = generator;
        }

        @Override
        public Number nextId(Object entity) {
            return generator.getUID();
        }
    }
}
