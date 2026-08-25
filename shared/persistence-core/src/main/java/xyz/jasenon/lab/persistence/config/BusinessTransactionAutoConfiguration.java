package xyz.jasenon.lab.persistence.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * 显式将默认事务管理器绑定到业务数据源。
 *
 * <p>UID 生成器会注册独立的 {@code uidDataSource}。存在多个数据源时，Spring Boot
 * 无法自动推断事务管理器，必须通过 bean 名明确选择业务 {@code dataSource}。</p>
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = TransactionAutoConfiguration.class
)
public class BusinessTransactionAutoConfiguration {

    @Bean(name = "transactionManager")
    @Primary
    @ConditionalOnBean(name = "dataSource")
    @ConditionalOnMissingBean(PlatformTransactionManager.class)
    public PlatformTransactionManager transactionManager(
            @Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
