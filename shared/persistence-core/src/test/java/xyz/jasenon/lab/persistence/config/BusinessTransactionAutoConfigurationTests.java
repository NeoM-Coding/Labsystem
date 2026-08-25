package xyz.jasenon.lab.persistence.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
class BusinessTransactionAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    BusinessTransactionAutoConfiguration.class,
                    TransactionAutoConfiguration.class
            ))
            .withUserConfiguration(TwoDataSources.class);

    @Test
    void transactionManagerUsesBusinessDataSourceWhenUidDataSourceAlsoExists() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataSourceTransactionManager.class);
            DataSourceTransactionManager manager = context.getBean(DataSourceTransactionManager.class);
            assertThat(manager.getDataSource()).isSameAs(context.getBean("dataSource"));
            assertThat(manager.getDataSource()).isNotSameAs(context.getBean("uidDataSource"));
            assertThat(context).hasBean("org.springframework.transaction.config.internalTransactionAdvisor");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSources {

        @Bean(name = "dataSource")
        DataSource dataSource() {
            return dataSource("jdbc:test:business");
        }

        @Bean(name = "uidDataSource")
        DataSource uidDataSource() {
            return dataSource("jdbc:test:uid");
        }

        private static DataSource dataSource(String url) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(url);
            return dataSource;
        }
    }
}
