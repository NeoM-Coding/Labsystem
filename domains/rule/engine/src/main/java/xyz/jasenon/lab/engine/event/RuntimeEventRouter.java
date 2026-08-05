package xyz.jasenon.lab.engine.event;

import org.springframework.stereotype.Component;
import xyz.jasenon.lab.engine.runtime.Runtime;
import xyz.jasenon.lab.engine.runtime.RuntimeSignal;

import java.util.Objects;

/**
 * 将事件类型分支隔离在 Engine 之外。
 */
@Component
public class RuntimeEventRouter {

    private final DeviceEventHandler deviceHandler;
    private final TimeEventHandler timeHandler;

    public RuntimeEventRouter(DeviceEventHandler deviceHandler, TimeEventHandler timeHandler) {
        this.deviceHandler = Objects.requireNonNull(deviceHandler, "deviceHandler");
        this.timeHandler = Objects.requireNonNull(timeHandler, "timeHandler");
    }

    public RuntimeSignal route(Runtime runtime, EngineEvent event) {
        if (event instanceof DeviceEvent deviceEvent) {
            return deviceHandler.handle(runtime, deviceEvent);
        }
        if (event instanceof TimeEvent timeEvent) {
            return timeHandler.handle(runtime, timeEvent);
        }
        throw new IllegalArgumentException("unsupported engine event: " + event.getClass().getName());
    }
}
