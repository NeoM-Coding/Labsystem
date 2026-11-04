package xyz.jasenon.lab.audit.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import xyz.jasenon.lab.audit.aspect.AuditLogAspect;
import xyz.jasenon.lab.audit.handler.AuditHandlerRegistry;
import xyz.jasenon.lab.audit.handler.AuditLogHandler;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;

import java.util.List;

@AutoConfiguration
@Import({AuditAuthorizationConfiguration.class, AuditPersistenceConfiguration.class})
@ConditionalOnProperty(prefix = "lab.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditHandlerRegistry auditHandlerRegistry(List<AuditLogHandler<?>> handlers) {
        return new AuditHandlerRegistry(handlers);
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLogAspect auditLogAspect(AuditHandlerRegistry registry, AuditLogStore store) {
        return new AuditLogAspect(registry, store);
    }
}
