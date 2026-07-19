package xyz.jasenon.lab.audit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import xyz.jasenon.lab.audit.aspect.AuditLogAspect;
import xyz.jasenon.lab.audit.handler.AuditHandlerRegistry;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.audit.persistence.MybatisAuditLogStore;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;

import java.util.List;

@AutoConfiguration
@MapperScan("xyz.jasenon.lab.audit.persistence.mapper")
@ConditionalOnProperty(prefix = "lab.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditHandlerRegistry auditHandlerRegistry(List<AuditLogHandler<?>> handlers) {
        return new AuditHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLogStore auditLogStore(AuditLogMapper mapper) {
        return new MybatisAuditLogStore(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLogAspect auditLogAspect(AuditHandlerRegistry registry, AuditLogStore store) {
        return new AuditLogAspect(registry, store);
    }
}
