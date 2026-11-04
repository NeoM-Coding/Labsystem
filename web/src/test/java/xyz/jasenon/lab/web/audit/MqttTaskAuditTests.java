package xyz.jasenon.lab.web.audit;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.MqttMultiTaskDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.audit.api.AuditAction;
import xyz.jasenon.lab.audit.handler.AuditHandlerRegistry;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MqttTaskAuditTests {

    private final AuditHandlerRegistry registry = new AuditHandlerRegistry(List.of(
            new MqttTaskAuditHandler(), new MqttMultiTaskAuditHandler()));

    @Test
    void singleTaskProducesDeviceAuditFragment() {
        var task = MqttTaskDto.of(CommandLine.OPEN_LIGHT, new int[]{1, 2}, DeviceType.Light, "light-1");
        var fragment = registry.handle(task).orElseThrow();

        assertThat(fragment.action()).isEqualTo(AuditAction.EDIT);
        assertThat(fragment.objectType()).isEqualTo("device");
        assertThat(fragment.objectId()).isEqualTo("light-1");
        assertThat(fragment.description()).contains("light-1", "Light", "打开灯光", "[1, 2]");
    }

    @Test
    void batchTaskRecordsAllTargetsWithoutClaimingDeviceSuccess() {
        var task = new MqttMultiTaskDto(CommandLine.CLOSE_LIGHT, new int[]{1, 2},
                DeviceType.Light, List.of("light-1", "light-2"));
        var fragment = registry.handle(task).orElseThrow();

        assertThat(fragment.objectId()).isEqualTo("[light-1, light-2]");
        assertThat(fragment.description()).contains("批量发送", "light-1", "light-2", "关闭灯光", "[1, 2]");
    }

    @Test
    void missingOptionalValuesDoNotBreakLogFormatting() {
        assertThat(new MqttTaskDto().log()).contains("未指定");
        assertThat(registry.handle(new MqttMultiTaskDto(null, null, null, null)))
                .isPresent();
    }
}
