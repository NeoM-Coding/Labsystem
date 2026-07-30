package xyz.jasenon.lab.edu.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class EduTransactionConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    PlatformTransactionManager transactionManager(
            @Qualifier("dataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(TransactionTemplate.class)
    TransactionTemplate transactionTemplate(
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager
    ) {
        return new TransactionTemplate(transactionManager);
    }
}
