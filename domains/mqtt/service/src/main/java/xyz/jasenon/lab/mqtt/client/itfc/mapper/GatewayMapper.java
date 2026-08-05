package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.device.model.gateway.gateways.RS485Gateway;

import java.util.List;

@Mapper
public interface GatewayMapper extends BaseMapper<RS485Gateway> {

    List<RS485Gateway> listAll();

    RS485Gateway getById(@Param("gateway_id") String gatewayId);

    boolean addRS485Gateway(@Param("gateway") RS485Gateway gateway);

    boolean updateRS485Gateway(@Param("gateway") RS485Gateway gateway);

    boolean removeRS485Gateway(@Param("gateway_id") String gatewayId);

}
