package xyz.jasenon.lab.common;

import xyz.jasenon.lab.common.model.device.DeviceType;

public interface Const {

    String TRACE_ID_KEY = "trace-id";
    interface Key {
        String SUFFIX = ":";

        String RECORD = "record";

        default String RECORD_KEY(DeviceType deviceType, String deviceId){
            return RECORD + SUFFIX + deviceType.name() + SUFFIX + deviceId;
        }


    }

}
