package xyz.jasenon.lab.audit.api;

import java.io.Serializable;

public interface Loggable extends Serializable {

    String log();

    default Class<? extends Loggable> eventType() {
        return getClass();
    }
}
