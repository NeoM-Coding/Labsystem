package xyz.jasenon.lab.persistence.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.sunjieyi60.uid.starter.UidGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 将 uid-springboot-starter 接入 MyBatis-Plus 的 ASSIGN_ID 策略。
 */
@AutoConfiguration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setOverflow(false);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

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
