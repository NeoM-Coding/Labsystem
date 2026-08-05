package xyz.jasenon.lab.persistence.config;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 为所有持久化服务提供一致的 Java Time JSON 支持。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
public class PersistenceJacksonConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper persistenceObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Bean
    public InitializingBean persistenceJavaTimeConfigurer(ObjectMapper objectMapper) {
        return () -> {
            objectMapper.registerModule(new JavaTimeModule());
            JacksonTypeHandler.setObjectMapper(objectMapper);
        };
    }
}
