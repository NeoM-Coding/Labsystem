package xyz.jasenon.lab.mqtt.db;

import io.github.sunjieyi60.uid.starter.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import xyz.jasenon.lab.common.config.MybatisPlusConfig;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = UidGeneratorDataSourceIsolationTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:mqtt_business;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "server.port=0",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "fun.uid.assigner-mode=db",
        "fun.uid.generator-mode=none",
        "fun.uid.datasource.driver-class-name=org.h2.Driver",
        "fun.uid.datasource.url=jdbc:h2:mem:mqtt_uid;MODE=MySQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:db/uid-generator-schema.sql'",
        "fun.uid.datasource.username=sa",
        "fun.uid.datasource.password="
})
class UidGeneratorDataSourceIsolationTests {

    private static final Logger log = LoggerFactory.getLogger(UidGeneratorDataSourceIsolationTests.class);

    private final TestUidEntityMapper mapper;
    private final UidGenerator uidGenerator;
    private final DataSource businessDataSource;
    private final DataSource uidDataSource;
    private final JdbcTemplate businessJdbc;
    private final JdbcTemplate uidJdbc;

    @Autowired
    UidGeneratorDataSourceIsolationTests(
            TestUidEntityMapper mapper,
            UidGenerator uidGenerator,
            @Qualifier("dataSource") DataSource businessDataSource,
            @Qualifier("uidDataSource") DataSource uidDataSource
    ) {
        this.mapper = mapper;
        this.uidGenerator = uidGenerator;
        this.businessDataSource = businessDataSource;
        this.uidDataSource = uidDataSource;
        this.businessJdbc = new JdbcTemplate(businessDataSource);
        this.uidJdbc = new JdbcTemplate(uidDataSource);
    }

    @BeforeEach
    void prepareBusinessSchema() {
        log.info("Prepare H2 schemas before test: business table test_uid_entity, uid table tf_ap_worker_node");
        businessJdbc.execute("""
                CREATE TABLE IF NOT EXISTS test_uid_entity (
                    id BIGINT NOT NULL PRIMARY KEY,
                    name VARCHAR(64)
                )
                """);
        businessJdbc.update("DELETE FROM test_uid_entity");
        uidJdbc.update("DELETE FROM tf_ap_worker_node WHERE id > 1");
        log.info("Schema prepared and test data cleaned");
    }

    @Test
    void mybatisPlusInsertUsesUidGeneratorForPrimaryKey() {
        log.info("Start testing MyBatis-Plus insert with UidGenerator assigned primary key");
        TestUidEntity entity = new TestUidEntity();
        entity.setName("created-by-mybatis-plus");

        int inserted = mapper.insert(entity);
        Integer saved = businessJdbc.queryForObject(
                "SELECT COUNT(*) FROM test_uid_entity WHERE id = ?",
                Integer.class,
                entity.getId()
        );
        log.info("Inserted rows: {}, generated id: {}, saved rows with generated id: {}", inserted, entity.getId(), saved);

        assertEquals(1, inserted);
        assertNotNull(entity.getId());
        assertTrue(entity.getId() > 0);
        assertEquals(1, saved);
    }

    @Test
    void uidGeneratorProducesIdsFromIndependentUidContext() {
        log.info("Start testing direct UidGenerator id generation from independent uid context");
        long first = uidGenerator.getUID();
        long second = uidGenerator.getUID();
        log.info("Generated uid sequence: first={}, second={}", first, second);

        assertTrue(first > 0);
        assertTrue(second > 0);
        assertNotEquals(first, second);
    }

    @Test
    void businessDataSourceAndUidDataSourceAreIsolated() {
        log.info("Start testing datasource isolation between business datasource and uid datasource");
        assertNotEquals(businessDataSource, uidDataSource);

        boolean businessTableExists = tableExists(businessJdbc, "test_uid_entity");
        boolean workerTableInBusiness = tableExists(businessJdbc, "tf_ap_worker_node");
        boolean workerTableInUid = tableExists(uidJdbc, "tf_ap_worker_node");
        log.info(
                "Datasource isolation check: business.test_uid_entity={}, business.tf_ap_worker_node={}, uid.tf_ap_worker_node={}",
                businessTableExists,
                workerTableInBusiness,
                workerTableInUid
        );

        assertTrue(businessTableExists);
        assertFalse(workerTableInBusiness);
        assertTrue(workerTableInUid);

        assertThrows(
                RuntimeException.class,
                () -> uidJdbc.queryForObject("SELECT COUNT(*) FROM test_uid_entity", Integer.class)
        );
        log.info("Confirmed uid datasource cannot query business table test_uid_entity");
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE lower(TABLE_NAME) = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(MybatisPlusConfig.class)
    static class TestApplication {
    }
}
