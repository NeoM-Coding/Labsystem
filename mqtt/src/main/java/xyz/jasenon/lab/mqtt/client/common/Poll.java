package xyz.jasenon.lab.mqtt.client.common;

import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.devices.*;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class Poll<REQ extends Task> implements Delayed {

    private REQ request;
    private long interval;
    private long nextTime;
    private long timeout;

    public Poll(REQ request){
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.interval = 1_000L;
        this.timeout = 1_500L;
        refresh();
    }

    public Poll(REQ request, long interval) {
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.interval = interval;
        this.timeout = 5000L;
        refresh();
    }

    public Poll(REQ request, long timeout, long interval){
        this.request = Objects.requireNonNull(request, "request must not be null");
        this.timeout = timeout;
        this.interval = interval;
        refresh();
    }

    public PendingRequest<REQ> poll(){
        return new PendingRequest<>(
                this.request, PendingRequest.Type.POLL, timeout, interval
        );
    }

    // 回填前调用
    public void refresh() {
        this.nextTime = System.currentTimeMillis() + interval;
    }

    public void changeInterval(long interval){
        this.interval = interval;
        refresh();
    }

    public void changeTimeout(long timeout){
        this.timeout = timeout;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        long delay = nextTime - System.currentTimeMillis();

        return unit.convert(
                delay,
                TimeUnit.MILLISECONDS
        );
    }

    // nextTime 小的在前
    @Override
    public int compareTo(Delayed o) {
        Poll<?> other = (Poll<?>) o;

        return Long.compare(
                this.nextTime,
                other.nextTime
        );
    }

    @Override
    public int hashCode(){
        return Objects.hash(request);
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Poll<?> poll = (Poll<?>) obj;
        return Objects.equals(this.request, poll.request);
    }

    public static Poll<MqttTask> of(Device device, TaskHelper helper){
        Objects.requireNonNull(helper,"task helper must not be not and managed by spring");
        if (device instanceof Access) {
            return of((Access) device, helper);
        }
        if (device instanceof AirCondition){
            return of((AirCondition) device, helper);
        }
        if (device instanceof CircuitBreak){
            return of((CircuitBreak) device, helper);
        }
        if (device instanceof Light){
            return of((Light) device, helper);
        }
        if (device instanceof Sensor){
            return of((Sensor) device, helper);
        }
        return null;
    }

    private static Poll<MqttTask> of(Access access, TaskHelper helper){
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_ACCESS_DATA, new int[]{},
                DeviceType.Access, access.getId()
        );
        MqttTask task = helper.help(dto);
        return new Poll<>(task);
    }

    private static Poll<MqttTask> of(AirCondition airCondition, TaskHelper helper){
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_AIR_CONDITION_DATA_RS485, new int[]{},
                DeviceType.AirCondition, airCondition.getId()
        );
        MqttTask task = helper.help(dto);
        return new Poll<>(task);
    }

    private static Poll<MqttTask> of(CircuitBreak circuitBreak, TaskHelper helper){
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_CIRCUITBREAK_DATA, new int[]{},
                DeviceType.CircuitBreak, circuitBreak.getId()
        );
        MqttTask task = helper.help(dto);
        return new Poll<>(task);
    }

    private static Poll<MqttTask> of(Light light, TaskHelper helper){
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_LIGHT_DATA, new int[]{},
                DeviceType.Light, light.getId()
        );
        MqttTask task = helper.help(dto);
        return new Poll<>(task);
    }

    private static Poll<MqttTask> of(Sensor sensor, TaskHelper helper){
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_SENSOR_DATA, new int[]{},
                DeviceType.Sensor, sensor.getId()
        );
        MqttTask task = helper.help(dto);
        return new Poll<>(task);
    }
}
