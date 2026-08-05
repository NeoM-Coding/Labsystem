package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.device.model.Device;

import java.util.List;

@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    Device getDeviceById(@Param("device_id") String deviceId);

    List<Device> listAll();

    List<Device> list(@Param("gateway_id") String gatewayId,
                      @Param("laboratory_id") String laboratoryId);

    List<Device> listByLaboratories(@Param("gateway_id") String gatewayId,
                                    @Param("laboratory_ids") List<String> laboratoryIds);

    boolean addDevice(@Param("device") Device device);

    boolean updateDevice(@Param("device") Device device);

    boolean removeDevice(@Param("device_id") String deviceId);

}
