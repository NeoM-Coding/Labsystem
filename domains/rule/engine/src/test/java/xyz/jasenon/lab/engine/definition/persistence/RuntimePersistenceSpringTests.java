package xyz.jasenon.lab.engine.definition.persistence;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ActionExecutionResult;
import xyz.jasenon.lab.engine.alert.persistence.PersistentRuleAlertHook;
import xyz.jasenon.lab.engine.alert.persistence.mapper.AlertLogMapper;
import xyz.jasenon.lab.engine.api.command.AlertLogListQuery;
import xyz.jasenon.lab.engine.notification.RuleExecutionNotice;
import xyz.jasenon.lab.engine.service.RuleAlertLogServiceImpl;
import xyz.jasenon.lab.persistence.config.MybatisPlusConfig;
import xyz.jasenon.lab.engine.RuleEngineApplication;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;

import java.util.List;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "dubbo.provider.export=false",
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

    @Autowired
    private PersistentRuleAlertHook alertHook;

    @Autowired
    private AlertLogMapper alertLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void persistsAlertAndQueriesItWithMybatisPlusPage() {
        Instant matchedAt = Instant.parse("2026-08-11T08:00:00Z");
        alertHook.onAlert(new RuleExecutionNotice(
                "spring-alert-event", "spring-alert-runtime", "spring-alert-group",
                "device-condition", "time-condition", matchedAt, matchedAt.plusSeconds(1), "trace-1",
                List.of(new RuleExecutionNotice.ActionResult(
                        0, Action.ActionType.Report, null, List.of("user-1"), Set.of(), "温度超过阈值",
                        ActionExecutionResult.Status.NOT_IMPLEMENTED, "external delivery is not implemented",
                        matchedAt.plusSeconds(1)
                ))
        ));

        RuleAlertLogServiceImpl service = new RuleAlertLogServiceImpl(alertLogMapper, objectMapper);
        var page = service.list(new AlertLogListQuery(
                1, 10, "spring-alert-runtime", null, "not_implemented", null, null
        )).data();

        assertEquals(1, page.total());
        assertEquals(1, page.records().size());
        assertEquals("温度超过阈值", page.records().get(0).content());
        assertEquals(List.of("user-1"), page.records().get(0).userIds());
    }
}
