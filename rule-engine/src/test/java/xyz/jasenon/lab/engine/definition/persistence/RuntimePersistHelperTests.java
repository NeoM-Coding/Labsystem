package xyz.jasenon.lab.engine.definition.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.RuleEngineApplication;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionGroupDefinition;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.runtime.RuntimeTable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = RuleEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:runtime_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
@Sql(
        statements = {
                "DELETE FROM rule_runtime_revision",
                "DELETE FROM rule_runtime"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class RuntimePersistHelperTests {

    @Autowired
    private RuntimePersistHelper helper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Engine engine;

    @Test
    void appendsImmutableRevisionForUpdateEnableAndDisable() {
        assertTrue(helper.register(revision("runtime-1", true, "26")));
        assertFalse(helper.register(revision("runtime-1", true, "26")));
        assertEquals(1, revisionCount("runtime-1"));
        assertTrue(helper.fetch().get(0).isEnabled());

        assertTrue(helper.disable("runtime-1"));
        assertEquals(2, revisionCount("runtime-1"));
        assertFalse(helper.fetch().get(0).isEnabled());

        assertTrue(helper.enable("runtime-1"));
        assertEquals(3, revisionCount("runtime-1"));
        assertTrue(helper.fetch().get(0).isEnabled());
        assertTrue(helper.enable("runtime-1"));
        assertEquals(3, revisionCount("runtime-1"));

        assertTrue(helper.update("runtime-1", revision("runtime-1", true, "30")));
        assertEquals(4, revisionCount("runtime-1"));
        assertEquals(
                "30",
                helper.fetch().get(0)
                        .deviceConditionGroups().get(0)
                        .conditions().get(0)
                        .value()
        );

        assertTrue(helper.remove("runtime-1"));
        assertTrue(helper.fetch().isEmpty());
        assertEquals(4, revisionCount("runtime-1"));
    }

    @Test
    void restoresOnlyEnabledRuntimeAfterServiceRestart() {
        assertTrue(helper.register(revision("enabled-runtime", true, "26")));
        assertTrue(helper.register(revision("disabled-runtime", false, "26")));

        engine.remove("enabled-runtime");
        engine.remove("disabled-runtime");
        helper.restoreEnabledRuntimes();

        RuntimeTable runtimeTable = (RuntimeTable) ReflectionTestUtils.getField(
                engine,
                "runtimeHelper"
        );
        assertTrue(runtimeTable.contains("enabled-runtime"));
        assertFalse(runtimeTable.contains("disabled-runtime"));
    }

    private int revisionCount(String runtimeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_runtime_revision WHERE runtime_id = ?",
                Integer.class,
                runtimeId
        );
    }

    private static RuntimeRevision revision(
            String runtimeId,
            boolean enabled,
            String threshold
    ) {
        return new RuntimeRevision(
                runtimeId,
                enabled,
                null,
                null,
                List.of(new DeviceConditionGroupDefinition(
                        "hot-room",
                        List.of(new DeviceConditionDefinition(
                                "temperature",
                                DeviceType.AirCondition,
                                "ac-1",
                                "roomTemperature",
                                Operator.GT,
                                threshold,
                                LogicType.AND
                        ))
                )),
                List.of(new TimeConditionGroupDefinition("always", List.of())),
                List.of(new ActionGroupDefinition(
                        "report-temperature",
                        "hot-room",
                        "always",
                        List.of()
                ))
        );
    }

}
