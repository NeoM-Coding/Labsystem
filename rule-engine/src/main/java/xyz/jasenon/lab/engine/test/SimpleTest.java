package xyz.jasenon.lab.engine.test;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.mqtt.protocol.command.CommandLine;
import xyz.jasenon.lab.device.model.DeviceType;
import xyz.jasenon.lab.engine.Engine;
import xyz.jasenon.lab.engine.action.ActionGroup;
import xyz.jasenon.lab.engine.action.ControlAction;
import xyz.jasenon.lab.engine.action.ReportAction;
import xyz.jasenon.lab.engine.eval.DeviceConditionGroup;
import xyz.jasenon.lab.engine.eval.EvalNode;
import xyz.jasenon.lab.engine.eval.LogicType;
import xyz.jasenon.lab.engine.eval.Operator;
import xyz.jasenon.lab.engine.listener.DeviceRecordChangeListener;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.time.CalendarConstraint;
import xyz.jasenon.lab.engine.time.TimeConditionGroup;
import xyz.jasenon.lab.engine.time.TimePointCondition;
import xyz.jasenon.lab.engine.time.TimeWindowCondition;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真实链路手工验证入口。
 *
 * <p>设备条件只接受 MQTT 模块经 Redis 发布的真实快照；控制动作通过 Dubbo
 * 调用真实 MqttIo。必须显式开启，避免普通启动误发设备控制指令。</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "lab.rule-engine.simple-test",
        name = "enabled",
        havingValue = "true"
)
public class SimpleTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

    private final Engine engine;
    private final DeviceRecordChangeListener deviceRecordListener;
    private final Environment environment;
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private String runtimeId;

    public SimpleTest(
            Engine engine,
            DeviceRecordChangeListener deviceRecordListener,
            Environment environment
    ) {
        this.engine = engine;
        this.deviceRecordListener = deviceRecordListener;
        this.environment = environment;
    }

    /**
     * ApplicationReady 后再注册，确保 Redis listener 与 Dubbo 引用已经完成初始化。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerSimpleRuntimeThenAcceptDeviceRecordToStartRuntimeFunctionTest() {
        if (!registered.compareAndSet(false, true)) {
            return;
        }

        runtimeId = property("runtime-id", "simple-real-chain-runtime");
        String sourceDeviceId = property("source-device-id", "5");
        String controlDeviceId = property("control-device-id", sourceDeviceId);
        String threshold = property("temperature-threshold", "26");
        int address = intProperty("control-address", 31);
        int selfId = intProperty("control-self-id", 6);
        long pointDelaySeconds = longProperty("time-point-delay-seconds", 30);
        ZoneId zoneId = ZoneId.of(property("zone-id", "Asia/Shanghai"));
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        DeviceConditionGroup sharedDeviceCondition = temperatureCondition(
                sourceDeviceId,
                threshold
        );
        ActionGroup normalWindowControl = normalWindowControlGroup(
                sharedDeviceCondition,
                now,
                controlDeviceId,
                address,
                selfId
        );
        ActionGroup crossMidnightReport = crossMidnightReportGroup(
                sharedDeviceCondition,
                now
        );
        ActionGroup timePointReport = timePointReportGroup(
                sharedDeviceCondition,
                now.plusSeconds(pointDelaySeconds)
        );

        Runtime runtime = new Runtime(
                runtimeId,
                List.of(normalWindowControl, crossMidnightReport, timePointReport)
        );
        engine.register(runtime);
        boolean replayed = deviceRecordListener.replay(
                DeviceType.AirCondition,
                sourceDeviceId
        );

        log.info(
                "[SimpleRuleTest] runtime registered; waiting for MQTT Redis snapshot, runtime-id:{}, "
                        + "source-device-id:{}, condition:roomTemperature>{}, control-device-id:{}, "
                        + "time-point:{}, current-state-replayed:{}",
                runtimeId,
                sourceDeviceId,
                threshold,
                controlDeviceId,
                now.plusSeconds(pointDelaySeconds),
                replayed
        );
    }

    @PreDestroy
    public void removeSimpleRuntime() {
        if (registered.compareAndSet(true, false) && runtimeId != null) {
            engine.remove(runtimeId);
        }
    }

    private ActionGroup normalWindowControlGroup(
            DeviceConditionGroup deviceCondition,
            ZonedDateTime now,
            String controlDeviceId,
            int address,
            int selfId
    ) {
        String actionGroupId = "simple-normal-window-control";
        TimeConditionGroup timeGroup = new TimeConditionGroup(
                "simple-normal-window",
                List.of(new TimeWindowCondition(
                        "all-day-window",
                        new CalendarConstraint(
                                now.toLocalDate(),
                                now.toLocalDate(),
                                Set.of(now.getDayOfWeek()),
                                now.getZone()
                        ),
                        LocalTime.MIN,
                        LocalTime.MAX
                ))
        );
        MqttTaskDto controlTask = MqttTaskDto.of(
                CommandLine.OPEN_AIR_CONDITION_RS485,
                new int[]{},
                DeviceType.AirCondition,
                controlDeviceId
        );
        return new ActionGroup(
                actionGroupId,
                deviceCondition,
                timeGroup,
                List.of(
                        new ControlAction(actionGroupId, controlTask),
                        report(
                                actionGroupId,
                                "普通时间窗口满足，已提交真实空调控制动作"
                        )
                )
        );
    }

    private ActionGroup crossMidnightReportGroup(
            DeviceConditionGroup deviceCondition,
            ZonedDateTime now
    ) {
        String actionGroupId = "simple-cross-midnight-report";
        LocalTime current = now.toLocalTime();
        TimeConditionGroup timeGroup = new TimeConditionGroup(
                "simple-cross-midnight-window",
                List.of(new TimeWindowCondition(
                        "active-cross-midnight-window",
                        new CalendarConstraint(
                                now.toLocalDate().minusDays(1),
                                now.toLocalDate().plusDays(1),
                                Set.of(),
                                now.getZone()
                        ),
                        current.minusMinutes(1),
                        current.minusMinutes(2)
                ))
        );
        return new ActionGroup(
                actionGroupId,
                deviceCondition,
                timeGroup,
                List.of(report(
                        actionGroupId,
                        "跨午夜时间窗口与设备条件同时满足"
                ))
        );
    }

    private ActionGroup timePointReportGroup(
            DeviceConditionGroup deviceCondition,
            ZonedDateTime occurrence
    ) {
        String actionGroupId = "simple-time-point-report";
        TimeConditionGroup timeGroup = new TimeConditionGroup(
                "simple-time-point",
                List.of(new TimePointCondition(
                        "startup-delayed-point",
                        new CalendarConstraint(
                                occurrence.toLocalDate(),
                                occurrence.toLocalDate(),
                                Set.of(occurrence.getDayOfWeek()),
                                occurrence.getZone()
                        ),
                        occurrence.toLocalTime()
                ))
        );
        return new ActionGroup(
                actionGroupId,
                deviceCondition,
                timeGroup,
                List.of(report(
                        actionGroupId,
                        "TimePoint 到达且设备条件满足"
                ))
        );
    }

    private static DeviceConditionGroup temperatureCondition(
            String sourceDeviceId,
            String threshold
    ) {
        EvalNode dummy = new EvalNode();
        dummy.setResult(true);

        EvalNode condition = new EvalNode();
        condition.setNodeId("simple-room-temperature");
        condition.setDeviceType(DeviceType.AirCondition);
        condition.setDeviceId(sourceDeviceId);
        condition.setField("roomTemperature");
        condition.setOperator(Operator.GT);
        condition.setValue(threshold);
        condition.setLogicToPrev(LogicType.AND);
        condition.setResult(false);
        dummy.setNext(condition);
        return new DeviceConditionGroup("simple-shared-device-condition", dummy);
    }

    private static ReportAction report(String actionGroupId, String content) {
        return new ReportAction(
                actionGroupId,
                List.of("simple-test-user"),
                EnumSet.of(ReportAction.ReportType.SMTP),
                content
        );
    }

    private String property(String name, String defaultValue) {
        return environment.getProperty(
                "lab.rule-engine.simple-test." + name,
                defaultValue
        );
    }

    private int intProperty(String name, int defaultValue) {
        return environment.getProperty(
                "lab.rule-engine.simple-test." + name,
                Integer.class,
                defaultValue
        );
    }

    private long longProperty(String name, long defaultValue) {
        return environment.getProperty(
                "lab.rule-engine.simple-test." + name,
                Long.class,
                defaultValue
        );
    }
}
