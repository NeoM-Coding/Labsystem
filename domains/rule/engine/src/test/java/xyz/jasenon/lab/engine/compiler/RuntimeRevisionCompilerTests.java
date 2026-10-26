package xyz.jasenon.lab.engine.compiler;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.Action;
import xyz.jasenon.lab.engine.definition.RuntimeRevision;
import xyz.jasenon.lab.engine.definition.RuntimeRevision.*;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeRevisionCompilerTests {
    @Test
    void controlActionsDoNotAddPollsForConditionDevices() {
        var conditions = List.of(
                new DeviceConditionDefinition("opened", DeviceType.AirCondition, "ac-1", "opened",
                        Operator.EQ, "true", LogicType.AND),
                new DeviceConditionDefinition("temperature", DeviceType.AirCondition, "ac-2", "roomTemperature",
                        Operator.GT, "26", LogicType.AND));
        var control = new ActionDefinition(ActionType.Control,
                new MqttTaskDto(CommandLine.OPEN_AIR_CONDITION_RS485, new int[0], DeviceType.AirCondition, "target"),
                List.of(), Set.of(), null);
        var report = new ActionDefinition(ActionType.Report, null, List.of("user-1"), Set.of(ReportType.SMTP), "matched");
        var revision = new RuntimeRevision("rule", null, null,
                List.of(new DeviceConditionGroupDefinition("devices", conditions)),
                List.of(new TimeConditionGroupDefinition("always", List.of())),
                List.of(new ActionGroupDefinition("actions", "devices", "always", List.of(control, report))));

        var plan = new RuntimeRevisionCompiler().compile(revision);
        var actions = plan.actionGroups().get("actions").actions();

        assertEquals(List.of(Action.ActionType.Control, Action.ActionType.Report),
                actions.stream().map(action -> action.type()).toList());
        assertEquals("target", actions.get(0).deviceId());
        assertEquals("matched", actions.get(1).content());
    }
}
