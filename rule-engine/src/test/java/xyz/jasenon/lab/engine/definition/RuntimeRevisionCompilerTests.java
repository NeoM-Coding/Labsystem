package xyz.jasenon.lab.engine.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionGroupDefinition;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.event.DeviceEventHandler;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeRevisionCompilerTests {

    private final RuntimeRevisionCompiler compiler = new RuntimeRevisionCompiler();

    @Test
    void compilesSharedConditionGroupsOnceAndFansOutCandidates() {
        Runtime runtime = compiler.compile(revision(
                new ActionGroupDefinition(
                        "notify-user",
                        "hot-room",
                        "always",
                        List.of(reportAction())
                ),
                new ActionGroupDefinition(
                        "notify-admin",
                        "hot-room",
                        "always",
                        List.of(reportAction())
                )
        ));

        assertSame(
                runtime.getActionGroups().get(0).getDeviceConditionGroup(),
                runtime.getActionGroups().get(1).getDeviceConditionGroup()
        );
        assertSame(
                runtime.getActionGroups().get(0).getTimeConditionGroup(),
                runtime.getActionGroups().get(1).getTimeConditionGroup()
        );
        assertTrue(runtime.getActionGroups().get(0).getActions().get(0) instanceof ReportAction);

        RuntimeSignal.StateChanged signal = (RuntimeSignal.StateChanged) new DeviceEventHandler().handle(
                runtime,
                new DeviceEvent(
                        DeviceType.AirCondition,
                        "ac-1",
                        "roomTemperature",
                        "27",
                        Instant.now()
                )
        );
        assertEquals(Set.of("notify-user", "notify-admin"), signal.candidateActionGroupIds());
    }

    @Test
    void rejectsDanglingConditionGroupReferenceBeforeRegistration() {
        RuntimeRevision invalid = revision(new ActionGroupDefinition(
                "invalid-group",
                "missing-device-group",
                "always",
                List.of()
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> compiler.compile(invalid)
        );

        assertTrue(error.getMessage().contains("missing deviceConditionGroupId"));
    }

    @Test
    void roundTripsWebJsonBeforeCompilation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RuntimeRevision original = revision(new ActionGroupDefinition(
                "notify-user",
                "hot-room",
                "always",
                List.of(reportAction())
        ));

        String json = objectMapper.writeValueAsString(original);
        RuntimeRevision restored = objectMapper.readValue(json, RuntimeRevision.class);
        Runtime runtime = compiler.compile(restored);

        assertEquals("web-runtime", runtime.getRuntimeId());
        assertEquals("hot-room", runtime.getActionGroups().get(0).getDeviceConditionGroupId());
        assertEquals("always", runtime.getActionGroups().get(0).getTimeConditionGroupId());
    }

    @Test
    void treatsLegacyJsonWithoutEnabledAsEnabled() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RuntimeRevision original = revision(new ActionGroupDefinition(
                "notify-user",
                "hot-room",
                "always",
                List.of(reportAction())
        ));
        var json = objectMapper.valueToTree(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("enabled");

        RuntimeRevision restored = objectMapper.treeToValue(json, RuntimeRevision.class);

        assertTrue(restored.isEnabled());
    }

    private static RuntimeRevision revision(ActionGroupDefinition... actionGroups) {
        return new RuntimeRevision(
                "web-runtime",
                null,
                null,
                List.of(new DeviceConditionGroupDefinition(
                        "hot-room",
                        List.of(new DeviceConditionDefinition(
                                "temperature-over-26",
                                DeviceType.AirCondition,
                                "ac-1",
                                "roomTemperature",
                                Operator.GT,
                                "26",
                                LogicType.AND
                        ))
                )),
                List.of(new TimeConditionGroupDefinition("always", List.of())),
                List.of(actionGroups)
        );
    }

    private static ActionDefinition reportAction() {
        return new ActionDefinition(
                Action.ActionType.Report,
                null,
                List.of("user-1"),
                Set.of(ReportAction.ReportType.SMTP),
                "Room temperature is too high"
        );
    }
}
