package xyz.jasenon.lab.engine.infrastructure.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
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
import xyz.jasenon.lab.engine.runtime.RuntimeSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
                "dubbo.provider.export=false",
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
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, MybatisPlusAutoConfiguration.class})
class RuntimePersistHelperTests {

    @Autowired
    private RuntimePersistHelper helper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Engine engine;

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

    @Test
    void appendsImmutableRevisionForUpdateEnableAndDisable() {
        assertTrue(helper.register(revision("runtime-1", true, "26")));
        RuntimeSnapshot registered = engine.runtime("runtime-1").orElseThrow();
        assertFalse(helper.register(revision("runtime-1", true, "26")));
        assertEquals(1, revisionCount("runtime-1"));
        assertTrue(helper.fetch().get(0).isEnabled());

        assertTrue(helper.disable("runtime-1"));
        assertEquals(2, revisionCount("runtime-1"));
        assertFalse(helper.fetch().get(0).isEnabled());
        assertTrue(engine.runtime("runtime-1").isEmpty());

        assertTrue(helper.enable("runtime-1"));
        assertEquals(3, revisionCount("runtime-1"));
        assertTrue(helper.fetch().get(0).isEnabled());
        RuntimeSnapshot reenabled = engine.runtime("runtime-1").orElseThrow();
        assertNotSame(registered, reenabled);
        assertTrue(helper.enable("runtime-1"));
        assertEquals(3, revisionCount("runtime-1"));

        assertTrue(helper.update("runtime-1", revision("runtime-1", true, "30")));
        assertNotSame(reenabled, engine.runtime("runtime-1").orElseThrow());
        assertEquals(4, revisionCount("runtime-1"));
        assertEquals(
                "30",
                helper.fetch().get(0)
                        .deviceConditionGroups().get(0)
                        .conditions().get(0)
                        .value()
        );

        assertTrue(helper.remove("runtime-1"));
        assertTrue(engine.runtime("runtime-1").isEmpty());
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

        assertTrue(engine.runtime("enabled-runtime").isPresent());
        assertTrue(engine.runtime("disabled-runtime").isEmpty());
    }

    private int revisionCount(String runtimeId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rule_runtime_revision WHERE runtime_id = ?",
                Integer.class,
                runtimeId
        );
    }

    @Test
    void rejectsTimePointPublicationAndSkipsLegacyRestore() throws Exception {
        var base = revision("legacy-point", true, "26");
        var point = new RuntimeRevision.TimeConditionDefinition("point", RuntimeRevision.TimeConditionType.TIME_POINT,
                null, null, java.util.Set.of(), "UTC", null, null, java.time.LocalTime.NOON);
        var legacy = new RuntimeRevision(base.runtimeId(), true, null, null, base.deviceConditionGroups(),
                List.of(new TimeConditionGroupDefinition("always", List.of(point))), base.actionGroups());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> helper.register(legacy));
        assertEquals(0, revisionCount("legacy-point"));
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(legacy);
        jdbcTemplate.update("INSERT INTO rule_runtime(id,runtime_id,runtime_name,owner_id,enabled,status,published_revision_no) VALUES(?,?,?,?,?,?,?)",
                "legacy-meta", "legacy-point", "legacy-point", "system", true, "PUBLISHED", 1);
        jdbcTemplate.update("INSERT INTO rule_runtime_revision(id,runtime_id,revision_no,schema_version,definition,created_by) VALUES(?,?,?,?,?,?)",
                "legacy-revision", "legacy-point", 1, 1, json, "system");
        helper.restoreEnabledRuntimes();
        assertTrue(engine.runtime("legacy-point").isEmpty());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> helper.enable("legacy-point"));
        assertTrue(helper.disable("legacy-point"));
        assertFalse(helper.fetch().stream().filter(r -> r.runtimeId().equals("legacy-point")).findFirst().orElseThrow().isEnabled());
    }

}
