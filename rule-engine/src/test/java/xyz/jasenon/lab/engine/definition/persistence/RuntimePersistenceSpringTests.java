package xyz.jasenon.lab.engine.definition.persistence;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import xyz.jasenon.lab.common.config.MybatisPlusConfig;
import xyz.jasenon.lab.engine.RuleEngineApplication;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(
        classes = RuleEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:runtime_spring;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/runtime-persistence-schema.sql",
                "lab.redis.enabled=false",
                "lab.rule-engine.persistence.enabled=true",
                "lab.rule-engine.simple-test.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.config-center.address=N/A",
                "spring.profiles.active=test",
                "fun.uid.assigner-mode=none"
        }
)
class RuntimePersistenceSpringTests {

    @Autowired
    private RuntimePersistHelper persistHelper;

    @Autowired
    private IdentifierGenerator identifierGenerator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsPersistenceBeanAndStoresDisabledRevision() {
        RuntimeRevision revision = new RuntimeRevision(
                "spring-persist-runtime",
                false,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );

        assertTrue(persistHelper.register(revision));
        assertTrue(persistHelper.fetch().stream()
                .anyMatch(item -> "spring-persist-runtime".equals(item.runtimeId())
                        && !item.isEnabled()));
        assertTrue(identifierGenerator instanceof MybatisPlusConfig.CustomIdGenerator);
        assertNotEquals(
                "spring-persist-runtime",
                jdbcTemplate.queryForObject(
                        "SELECT id FROM rule_runtime WHERE runtime_id = ?",
                        String.class,
                        "spring-persist-runtime"
                )
        );
    }
}
