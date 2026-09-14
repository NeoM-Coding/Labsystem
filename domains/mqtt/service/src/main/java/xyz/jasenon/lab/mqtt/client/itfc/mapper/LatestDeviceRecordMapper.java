package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.device.model.records.AccessRecord;
import xyz.jasenon.lab.device.model.records.AirConditionRecord;
import xyz.jasenon.lab.device.model.records.CircuitBreakRecord;
import xyz.jasenon.lab.device.model.records.LightRecord;
import xyz.jasenon.lab.device.model.records.SensorRecord;

@Mapper
public interface LatestDeviceRecordMapper {

    AccessRecord latestAccess(@Param("device_id") String deviceId);

    AirConditionRecord latestAirCondition(@Param("device_id") String deviceId);

    CircuitBreakRecord latestCircuitBreak(@Param("device_id") String deviceId);

    LightRecord latestLight(@Param("device_id") String deviceId);

    SensorRecord latestSensor(@Param("device_id") String deviceId);
}
