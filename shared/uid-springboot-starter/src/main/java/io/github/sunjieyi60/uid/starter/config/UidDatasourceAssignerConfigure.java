package io.github.sunjieyi60.uid.starter.config;

import io.github.sunjieyi60.uid.starter.constant.Mode;
import io.github.sunjieyi60.uid.starter.properties.UidGeneratorProperties;
import io.github.sunjieyi60.uid.starter.worker.DatasourceWorkerIdAssigner;
import io.github.sunjieyi60.uid.starter.worker.WorkerIdAssigner;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * 线上环境依赖于数据库生成 WorkId，用于自动生成其对应的 WorkId 以及避免重复。
 * <p>
 * 该配置使用 <b>独立数据源</b>（前缀 {@code fun.uid.datasource}），
 * 不与 Spring 主体共享 DataSource，避免对主业务数据库产生干扰。
 *
 * @author MrDJun 2021/5/6
 * @refactor Jasenon 2026/5/1
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "fun.uid.assigner-mode", havingValue = Mode.DB)
public class UidDatasourceAssignerConfigure {

    /**
     * 创建 UidGenerator 独立数据源
     */
    @Bean(name = "uidDataSource")
    public DataSource uidDataSource(UidGeneratorProperties properties) {
        UidGeneratorProperties.Datasource ds = properties.getDatasource();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(ds.getDriverClassName());
        dataSource.setUrl(ds.getUrl());
        dataSource.setUsername(ds.getUsername());
        dataSource.setPassword(ds.getPassword());
        log.info("UidGenerator independent DataSource initialized, url: {}", ds.getUrl());
        return dataSource;
    }

    @Bean(name = "uidSqlSessionFactory")
    public SqlSessionFactory uidSqlSessionFactory(@Qualifier("uidDataSource") DataSource uidDataSource) throws Exception {
        SqlSessionFactoryBean sqlSessionFactory = new SqlSessionFactoryBean();
        sqlSessionFactory.setDataSource(uidDataSource);
        sqlSessionFactory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/META-INF/mapper/*.xml"));
        return sqlSessionFactory.getObject();
    }

    /**
     * 基于 DB 生成节点 ID（主选）
     */
    @Bean
    @ConditionalOnMissingBean(DatasourceWorkerIdAssigner.class)
    public WorkerIdAssigner disposableWorkerIdAssigner(@Qualifier("uidSqlSessionFactory") SqlSessionFactory uidSqlSessionFactory) {
        log.info("WorkerIdAssigner turn on datasource (independent mode)");
        return new DatasourceWorkerIdAssigner(uidSqlSessionFactory);
    }
}
