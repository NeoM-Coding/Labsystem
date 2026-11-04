package xyz.jasenon.lab.audit.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.jasenon.lab.audit.persistence.AuditLogStore;
import xyz.jasenon.lab.audit.persistence.MybatisAuditLogStore;
import xyz.jasenon.lab.audit.persistence.mapper.AuditLogMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lab.audit", name = "store", havingValue = "local", matchIfMissing = true)
@MapperScan(basePackages = "xyz.jasenon.lab.audit.persistence.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class AuditPersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AuditLogStore auditLogStore(AuditLogMapper mapper) {
        return new MybatisAuditLogStore(mapper);
    }
}
