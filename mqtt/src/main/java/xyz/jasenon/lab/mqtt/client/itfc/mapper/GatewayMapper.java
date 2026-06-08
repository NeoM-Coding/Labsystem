package xyz.jasenon.lab.mqtt.client.itfc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import xyz.jasenon.lab.common.model.gateway.gateways.RS485Gateway;

import java.util.List;

@Mapper
public interface GatewayMapper {

    @Select("SELECT * FROM `gateway` WHERE `delete_at` IS NULL AND `gateway_type` = 'RS485'")
    List<RS485Gateway> listAll();

}
