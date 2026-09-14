package xyz.jasenon.lab.engine.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.PollAction;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionType;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ActionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.DeviceConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TimeConditionGroupDefinition;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.ReportType;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.TriggerMode;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeRevisionCompilerTests {

    private final RuntimeRevisionCompiler compiler = new RuntimeRevisionCompiler();

    @Test
    void compilesSharedConditionGroupsOnceAndFansOutCandidates() {
        RuntimePlan plan = compiler.compile(revision(
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

        assertEquals(1, plan.deviceChains().size());
        assertEquals("hot-room", plan.actionGroups().get(0).deviceConditionGroupId());
        assertEquals("hot-room", plan.actionGroups().get(1).deviceConditionGroupId());
        assertTrue(plan.actionGroups().get(0).actions().get(0) instanceof ReportAction);
        assertTrue(plan.actionGroups().get(0).timeConditionGroup()
                == plan.actionGroups().get(1).timeConditionGroup());
        assertEquals(1, plan.requiredEventKeys().size());
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
        RuntimePlan runtime = compiler.compile(restored);

        assertEquals("web-runtime", runtime.runtimeId());
        assertEquals("hot-room", runtime.actionGroups().get(0).deviceConditionGroupId());
        assertEquals("always", runtime.actionGroups().get(0).timeConditionGroupId());
    }

    @Test
    void appliesDefaultsToLegacyJson() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RuntimeRevision original = revision(new ActionGroupDefinition(
                "notify-user",
                "hot-room",
                "always",
                List.of(reportAction())
        ));
        var json = objectMapper.valueToTree(original);
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("enabled");
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.get("actionGroups").get(0))
                .remove("triggerMode");

        RuntimeRevision restored = objectMapper.treeToValue(json, RuntimeRevision.class);

        assertTrue(restored.isEnabled());
        assertEquals(TriggerMode.EDGE, restored.actionGroups().get(0).triggerMode());
    }

    @Test
    void compilesConditionDevicePollsAfterConfiguredControlActions() {
        MqttTaskDto control = MqttTaskDto.of(
                CommandLine.CLOSE_AIR_CONDITION_RS485,
                new int[0],
                DeviceType.AirCondition,
                "ac-target"
        );
        ActionDefinition definition = new ActionDefinition(
                ActionType.Control, control, List.of(), Set.of(), null
        );
        RuntimePlan plan = compiler.compile(revision(new ActionGroupDefinition(
                "keep-cool",
                "hot-room",
                "always",
                TriggerMode.RECONCILE,
                List.of(definition)
        )));

        var actionGroup = plan.actionGroups().get(0);
        assertEquals(TriggerMode.RECONCILE, actionGroup.triggerMode());
        assertTrue(actionGroup.actions().get(0) instanceof ControlAction);
        PollAction poll = (PollAction) actionGroup.actions().get(1);
        assertEquals("ac-1", poll.query().getDeviceId());
        assertEquals(CommandLine.REQUEST_AIR_CONDITION_DATA_RS485,
                poll.query().getCommandLine());
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
                ActionType.Report,
                null,
                List.of("user-1"),
                Set.of(ReportType.SMTP),
                "Room temperature is too high"
        );
    }
}
