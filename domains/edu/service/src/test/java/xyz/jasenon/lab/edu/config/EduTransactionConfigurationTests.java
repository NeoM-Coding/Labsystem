package xyz.jasenon.lab.edu.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EduTransactionConfigurationTests {

    @Test
    void transactionManagerUsesPrimaryApplicationDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();

        PlatformTransactionManager transactionManager = new EduTransactionConfiguration()
                .transactionManager(dataSource);

        assertTrue(transactionManager instanceof DataSourceTransactionManager);
        assertSame(dataSource, ((DataSourceTransactionManager) transactionManager).getDataSource());
    }

    @Test
    void transactionTemplateUsesPrimaryApplicationTransactionManager() {
        PlatformTransactionManager transactionManager = new DataSourceTransactionManager();

        TransactionTemplate template = new EduTransactionConfiguration()
                .transactionTemplate(transactionManager);

        assertSame(transactionManager, template.getTransactionManager());
    }
}
