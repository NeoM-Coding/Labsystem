package xyz.jasenon.lab.mqtt.client.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttMapperXmlTests {

    @Test
    void deviceMapperContainsCrudStatements() throws IOException {
        Configuration configuration = parse("mapper/DeviceMapper.xml");

        assertTrue(configuration.hasStatement(
                "xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper.addDevice"));
        assertTrue(configuration.hasStatement(
                "xyz.jasenon.lab.mqtt.client.itfc.mapper.DeviceMapper.removeDevice"));
    }

    @Test
    void gatewayMapperContainsCrudStatements() throws IOException {
        Configuration configuration = parse("mapper/GatewayMapper.xml");

        assertTrue(configuration.hasStatement(
                "xyz.jasenon.lab.mqtt.client.itfc.mapper.GatewayMapper.addRS485Gateway"));
        assertTrue(configuration.hasStatement(
                "xyz.jasenon.lab.mqtt.client.itfc.mapper.GatewayMapper.removeRS485Gateway"));
    }

    private Configuration parse(String resource) throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
