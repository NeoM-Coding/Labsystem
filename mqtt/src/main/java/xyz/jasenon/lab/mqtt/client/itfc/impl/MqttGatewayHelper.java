package xyz.jasenon.lab.mqtt.client.itfc.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.jasenon.lab.common.model.gateway.Gateway;
import xyz.jasenon.lab.common.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.common.model.gateway.gateways.SocketGateway;
import xyz.jasenon.lab.mqtt.client.itfc.GatewayHelper;
import xyz.jasenon.lab.mqtt.client.itfc.mapper.GatewayMapper;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Service
public class MqttGatewayHelper implements GatewayHelper {

    private final GatewayMapper gatewayMapper;

    @Override
    public List<RS485Gateway> listAll() {
        return gatewayMapper.listAll();
    }

    @Override
    public List<Gateway> listRange(int workerId, int works) {
        throw new UnsupportedOperationException("not implements now!");
    }
}
