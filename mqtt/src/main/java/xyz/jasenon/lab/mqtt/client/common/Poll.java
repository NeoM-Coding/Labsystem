package xyz.jasenon.lab.mqtt.client.common;

import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.model.device.Device;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class Poll<REQ extends Task> implements Delayed {

    private REQ request;
    private long interval;
    private long nextTime;
    private long timeout;

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

//    public static Poll<MqttTask> of(DeviceType deviceType, Device device){
//
//    }
}
