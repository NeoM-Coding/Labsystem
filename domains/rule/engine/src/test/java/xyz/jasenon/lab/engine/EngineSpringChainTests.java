package xyz.jasenon.lab.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.event.DeviceEvent;
import xyz.jasenon.lab.engine.runtime.Runtime;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = RuleEngineApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "lab.redis.enabled=false",
                "lab.rule-engine.persistence.enabled=false",
                "dubbo.registry.address=N/A",
                "dubbo.config-center.address=N/A",
                "spring.profiles.active=test",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "fun.uid.assigner-mode=none"
        }
)
@ExtendWith(OutputCaptureExtension.class)
class EngineSpringChainTests {

    @Autowired
    private Engine engine;

    @Test
    void springManagedSchedulerLogsTriggeredActionGroup(CapturedOutput output) throws Exception {
        Runtime runtime = new Runtime("spring-runtime-1");
        runtime.registerActionGroup(new ActionGroup(
                "spring-high-temperature",
                chain("ac-1", "roomTemperature", Operator.GT, "26")
        ));
        runtime.registerActionGroup(new ActionGroup(
                "spring-too-high-temperature",
                chain("ac-1", "roomTemperature", Operator.GT, "30")
        ));
        engine.register(runtime);

        engine.accept(new DeviceEvent(DeviceType.AirCondition, "ac-1", "roomTemperature", "27", Instant.now()));

        assertTrue(awaitOutput(output, "runtime-id:spring-runtime-1, action-group-id:spring-high-temperature"));
        assertFalse(output.getOut().contains("action-group-id:spring-too-high-temperature"));

        engine.remove("spring-runtime-1");
    }

    private static EvalNode chain(String deviceId, String field, Operator operator, String value) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);

        EvalNode node = new EvalNode();
        node.setNodeId("node-1");
        node.setDeviceId(deviceId);
        node.setDeviceType(DeviceType.AirCondition);
        node.setField(field);
        node.setOperator(operator);
        node.setValue(value);
        node.setLogicToPrev(LogicType.AND);
        node.setResult(false);
        dummy.setNext(node);
        return dummy;
    }

    private static boolean awaitOutput(CapturedOutput output, String expected) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (output.getOut().contains(expected)) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
