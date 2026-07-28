package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.device.model.records.AccessRecord;
import xyz.jasenon.lab.device.model.records.AirConditionRecord;
import xyz.jasenon.lab.device.model.records.CircuitBreakRecord;
import xyz.jasenon.lab.device.model.records.LightRecord;
import xyz.jasenon.lab.device.model.records.SensorRecord;

import java.util.List;

@Mapper
public interface LatestDeviceRecordMapper {

    List<AccessRecord> latestAccess(@Param("device_ids") List<String> deviceIds);

    List<AirConditionRecord> latestAirCondition(@Param("device_ids") List<String> deviceIds);

    List<CircuitBreakRecord> latestCircuitBreak(@Param("device_ids") List<String> deviceIds);

    List<LightRecord> latestLight(@Param("device_ids") List<String> deviceIds);

    List<SensorRecord> latestSensor(@Param("device_ids") List<String> deviceIds);
}
