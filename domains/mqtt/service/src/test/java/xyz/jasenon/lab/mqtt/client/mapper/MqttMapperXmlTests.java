package xyz.jasenon.lab.mqtt.client.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void latestTelemetryQueriesUsePerDeviceIndexTopOne() throws IOException {
        Configuration configuration = parse("mapper/LatestDeviceRecordMapper.xml");
        Map<String, Object> parameters = Map.of("device_id", "device-1");

        List.of(
                "latestAccess",
                "latestAirCondition",
                "latestCircuitBreak",
                "latestLight",
                "latestSensor"
        ).forEach(statement -> {
            String sql = configuration.getMappedStatement(
                            "xyz.jasenon.lab.mqtt.client.itfc.mapper.LatestDeviceRecordMapper." + statement
                    )
                    .getBoundSql(parameters)
                    .getSql()
                    .replaceAll("\\s+", " ");
            assertTrue(sql.contains("r.device_id = ?"));
            assertTrue(sql.contains("r.delete_at IS NULL"));
            assertTrue(sql.contains("ORDER BY r.create_at DESC, r.id DESC"));
            assertTrue(sql.contains("LIMIT 1"));
            assertFalse(sql.contains("ROW_NUMBER() OVER"));
            assertFalse(sql.contains("NOT EXISTS"));
        });
    }

    private Configuration parse(String resource) throws IOException {
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }
}
