package xyz.jasenon.lab.engine.definition.persistence;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RulePersistenceConfigurationTests {

    @Test
    void businessMappersUsePrimaryBusinessSqlSessionFactory() {
        MapperScan mapperScan = RulePersistenceConfiguration.class.getAnnotation(MapperScan.class);

        assertNotNull(mapperScan);
        assertEquals("sqlSessionFactory", mapperScan.sqlSessionFactoryRef());
    }
}
