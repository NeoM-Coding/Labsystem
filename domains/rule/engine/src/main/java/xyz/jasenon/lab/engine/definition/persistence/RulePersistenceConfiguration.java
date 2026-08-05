package xyz.jasenon.lab.engine.definition.persistence;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "lab.rule-engine.persistence",
        name = "enabled",
        havingValue = "true"
)
public class RulePersistenceConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    PlatformTransactionManager transactionManager(
            @Qualifier("dataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }
}
