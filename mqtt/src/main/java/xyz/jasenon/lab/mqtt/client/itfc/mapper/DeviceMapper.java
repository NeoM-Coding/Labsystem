package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.common.model.device.Device;

import java.util.List;

@Mapper
public interface DeviceMapper {

    Device getDeviceById(@Param("device_id") String deviceId);

    List<Device> listAll();

    boolean updateDevice(@Param("device") Device device);

}
