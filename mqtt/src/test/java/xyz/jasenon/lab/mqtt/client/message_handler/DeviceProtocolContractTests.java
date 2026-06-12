package xyz.jasenon.lab.mqtt.client.message_handler;

import org.junit.jupiter.api.Test;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;
import xyz.jasenon.lab.common.command.CommandLine;
import xyz.jasenon.lab.common.command.checker.CrcChecker;
import xyz.jasenon.lab.common.command.checker.SumChecker;
import xyz.jasenon.lab.common.model.device.DeviceType;
import xyz.jasenon.lab.common.model.device.records.AccessRecord;
import xyz.jasenon.lab.common.model.device.records.AirConditionRecord;
import xyz.jasenon.lab.common.model.device.records.CircuitBreakRecord;
import xyz.jasenon.lab.common.model.device.records.LightRecord;
import xyz.jasenon.lab.common.model.device.records.SensorRecord;
import xyz.jasenon.lab.mqtt.client.message_handler.handlers.AccessMessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.handlers.AirConditionMessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.handlers.CircuitBreakMessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.handlers.LightMessageHandler;
import xyz.jasenon.lab.mqtt.client.message_handler.handlers.SensorMessageHandler;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceProtocolContractTests {

    private static final String GATEWAY_ID = "gateway-contract-test";

    /**
     * 验证门禁请求数据链路:
     * MqttTaskDto -> MqttTask.convert() 请求 payload -> mqtt-mock 风格响应
     * payload -> AccessMessageHandler.decode() 记录。
     */
    @Test
    void requestAccessDataProtocolMatchesCommandLineMockAndDecoder() {
        String deviceId = "access-1";
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_ACCESS_DATA,
                new int[]{1},
                DeviceType.Access,
                deviceId
        );

        MqttTask request = MqttTask.fromDto(GATEWAY_ID, dto);
        assertArrayEquals(
                bytes(1, 3, 1, 0, 0, 0, 5),
                request.getPayload(),
                "CommandLine.REQUEST_ACCESS_DATA should produce the documented request payload"
        );

        byte[] response = mqttMockRequestAccessDataResponse(request.getPayload());
        assertArrayEquals(
                bytes(1, 3, 1, 255, 255, 255, 5, 10),
                response,
                "mqtt-mock response should keep the request address and return access status payload"
        );
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(response));

        AccessRecord record = new TestableAccessMessageHandler().decodeForTest(response);
        record.setDeviceId(dto.getDeviceId());

        assertEquals(deviceId, record.getDeviceId());
        assertEquals(1, record.getAddress());
        assertTrue(record.isOpened());
        assertTrue(record.isLocked());
        assertEquals(1, record.getLockStatus());
        assertEquals(5, record.getDelayTime());
    }

    /**
     * 验证空调请求数据链路:
     * 当前 CommandLine 使用 SIGN_SUM 的 RS485 查询格式，mock 以一组固定但可解码的
     * 空调状态响应，最终应映射为 AirConditionRecord。
     */
    @Test
    void requestAirConditionDataProtocolMatchesCommandLineMockAndDecoder() {
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_AIR_CONDITION_DATA_RS485,
                new int[]{31, 6},
                DeviceType.AirCondition,
                "air-condition-31-6"
        );

        MqttTask request = MqttTask.fromDto(GATEWAY_ID, dto);
        assertArrayEquals(
                SumChecker.generateSgPayload(bytes(31, 6, 255, 255, 255, 255, 255, 255, 255)),
                request.getPayload(),
                "CommandLine.REQUEST_AIR_CONDITION_DATA_RS485 should produce a signed-sum request payload"
        );

        byte[] response = mqttMockRequestAirConditionDataResponse(request.getPayload());
        assertTrue(SumChecker.verifyCheckSum(response));

        AirConditionRecord record = new TestableAirConditionMessageHandler().decodeForTest(response);
        record.setDeviceId(dto.getDeviceId());

        assertEquals(dto.getDeviceId(), record.getDeviceId());
        assertEquals(31, record.getAddress());
        assertEquals(6, record.getSelfId());
        assertTrue(record.isOpened());
        assertEquals(AirConditionRecord.Mode.Cooling, record.getMode());
        assertEquals(25, record.getTemperature());
        assertEquals(AirConditionRecord.Speed.High, record.getSpeed());
        assertEquals(24, record.getRoomTemperature());
        assertEquals(0, record.getErrorCode());
    }

    /**
     * 验证断路器请求数据链路:
     * CRC16 查询指令应被 mock 识别，mock 返回包含状态位和多个 little-endian float
     * 遥测值的大 payload，最终应被 CircuitBreakMessageHandler.decode() 解析。
     */
    @Test
    void requestCircuitBreakDataProtocolMatchesCommandLineMockAndDecoder() {
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_CIRCUITBREAK_DATA,
                new int[]{11},
                DeviceType.CircuitBreak,
                "circuit-break-11"
        );

        MqttTask request = MqttTask.fromDto(GATEWAY_ID, dto);
        assertArrayEquals(
                CrcChecker.generatePayload(bytes(11, 3, 0, 24, 0, 116)),
                request.getPayload(),
                "CommandLine.REQUEST_CIRCUITBREAK_DATA should produce a CRC16 request payload"
        );

        byte[] response = mqttMockRequestCircuitBreakDataResponse(request.getPayload());
        assertTrue(CrcChecker.varify(response));

        CircuitBreakRecord record = new TestableCircuitBreakMessageHandler().decodeForTest(response);
        record.setDeviceId(dto.getDeviceId());

        assertEquals(dto.getDeviceId(), record.getDeviceId());
        assertEquals(11, record.getAddress());
        assertTrue(record.isFixed());
        assertTrue(record.isOpened());
        assertFalse(record.isLocked());
        assertEquals(0.131f, record.getLeakage(), 0.0001f);
        assertEquals(28.5f, record.getTemperature(), 0.0001f);
        assertEquals(221.0f, record.getVoltage(), 0.0001f);
        assertEquals(1.5f, record.getCurrent(), 0.0001f);
        assertEquals(271.0f, record.getPower(), 0.0001f);
        assertEquals(1245.5f, record.getEnergy(), 0.0001f);
    }

    /**
     * 验证灯光请求数据链路:
     * 请求 payload 使用无符号和校验，mock 根据地址和 selfId 返回灯光开关和锁定状态，
     * 最终应映射为 LightRecord。
     */
    @Test
    void requestLightDataProtocolMatchesCommandLineMockAndDecoder() {
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_LIGHT_DATA,
                new int[]{41, 2},
                DeviceType.Light,
                "light-41-2"
        );

        MqttTask request = MqttTask.fromDto(GATEWAY_ID, dto);
        assertArrayEquals(
                SumChecker.generateUnSgPayload(bytes(41, 3, 2, 0, 0, 0)),
                request.getPayload(),
                "CommandLine.REQUEST_LIGHT_DATA should produce an unsigned-sum request payload"
        );

        byte[] response = mqttMockRequestLightDataResponse(request.getPayload());
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(response));

        LightRecord record = new TestableLightMessageHandler().decodeForTest(response);
        record.setDeviceId(dto.getDeviceId());

        assertEquals(dto.getDeviceId(), record.getDeviceId());
        assertEquals(41, record.getAddress());
        assertEquals(2, record.getSelfId());
        assertTrue(record.isOpened());
        assertTrue(record.isLocked());
    }

    /**
     * 验证传感器请求数据链路:
     * 传感器请求保持 7 字节无符号和校验格式，mock 返回温湿度、光照、烟雾的多设备数据，
     * 最终应被 SensorMessageHandler.decode() 按 big-endian 数值解析。
     */
    @Test
    void requestSensorDataProtocolMatchesCommandLineMockAndDecoder() {
        MqttTaskDto dto = MqttTaskDto.of(
                CommandLine.REQUEST_SENSOR_DATA,
                new int[]{61, 1},
                DeviceType.Sensor,
                "sensor-61-1"
        );

        MqttTask request = MqttTask.fromDto(GATEWAY_ID, dto);
        assertArrayEquals(
                SumChecker.generateUnSgPayload(bytes(61, 3, 1, 0, 0, 0)),
                request.getPayload(),
                "CommandLine.REQUEST_SENSOR_DATA should produce the 7-byte unsigned-sum request payload"
        );

        byte[] response = mqttMockRequestSensorDataResponse(request.getPayload());
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(response));

        SensorRecord record = new TestableSensorMessageHandler().decodeForTest(response);
        record.setDeviceId(dto.getDeviceId());

        assertEquals(dto.getDeviceId(), record.getDeviceId());
        assertEquals(61, record.getAddress());
        assertEquals(1, record.getSelfId());
        assertEquals(25.1, record.getTemperature(), 0.0001);
        assertEquals(56.4, record.getHumidity(), 0.0001);
        assertEquals(161.1, record.getLight(), 0.0001);
        assertEquals(18, record.getSmoke());
    }

    private static byte[] mqttMockRequestAccessDataResponse(byte[] request) {
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(request));
        int address = request[0] & 0xFF;
        return SumChecker.generateUnSgPayload(bytes(address, 3, 1, 255, 255, 255, 5));
    }

    private static byte[] mqttMockRequestAirConditionDataResponse(byte[] request) {
        assertTrue(SumChecker.verifyCheckSum(request));
        int address = request[0] & 0xFF;
        int selfId = request[1] & 0xFF;
        return SumChecker.generateSgPayload(bytes(address, selfId, 1, 2, 25, 3, 24, 0));
    }

    private static byte[] mqttMockRequestCircuitBreakDataResponse(byte[] request) {
        assertTrue(CrcChecker.varify(request));
        int address = request[0] & 0xFF;
        byte[] body = new byte[219];
        body[0] = (byte) address;
        body[1] = 0x03;
        body[2] = (byte) 0xE8;
        body[3] = 0x01;
        body[4] = (byte) (address % 2 == 0 ? 0x03 : 0x01);
        writeFloatLE(body, 7, 0.12f + address / 1000f);
        writeFloatLE(body, 11, 26.5f + address % 3);
        writeFloatLE(body, 55, 220f + address % 5);
        writeFloatLE(body, 119, 1.2f + address % 4 / 10f);
        writeFloatLE(body, 151, 260f + address);
        writeFloatLE(body, 215, 1234.5f + address);
        return CrcChecker.generatePayload(body);
    }

    private static byte[] mqttMockRequestLightDataResponse(byte[] request) {
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(request));
        int address = request[0] & 0xFF;
        int selfId = request[2] & 0xFF;
        return SumChecker.generateUnSgPayload(bytes(address, 3, selfId, 255, 255, 0));
    }

    private static byte[] mqttMockRequestSensorDataResponse(byte[] request) {
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(request));
        int address = request[0] & 0xFF;
        int selfId = request[2] & 0xFF;
        int temperatureTenths = vary(245, address, selfId);
        int humidityTenths = vary(558, address, selfId);
        int lightTenths = 1000 + address * 10 + selfId;
        int smoke = vary(12, address, selfId);
        return SumChecker.generateUnSgPayload(bytes(
                address,
                3,
                selfId,
                u16High(temperatureTenths),
                u16Low(temperatureTenths),
                u16High(humidityTenths),
                u16Low(humidityTenths),
                u32Byte(lightTenths, 24),
                u32Byte(lightTenths, 16),
                u32Byte(lightTenths, 8),
                u32Byte(lightTenths, 0),
                u16High(smoke),
                u16Low(smoke)
        ));
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) (values[i] & 0xFF);
        }
        return bytes;
    }

    private static void writeFloatLE(byte[] target, int offset, float value) {
        int bits = Float.floatToIntBits(value);
        target[offset] = (byte) (bits & 0xFF);
        target[offset + 1] = (byte) ((bits >>> 8) & 0xFF);
        target[offset + 2] = (byte) ((bits >>> 16) & 0xFF);
        target[offset + 3] = (byte) ((bits >>> 24) & 0xFF);
    }

    private static int vary(int base, int address, int selfId) {
        return (base + address % 7 + selfId % 5) & 0xFFFF;
    }

    private static int u16High(int value) {
        return (value >>> 8) & 0xFF;
    }

    private static int u16Low(int value) {
        return value & 0xFF;
    }

    private static int u32Byte(int value, int shift) {
        return (value >>> shift) & 0xFF;
    }

    private static class TestableAccessMessageHandler extends AccessMessageHandler {

        private TestableAccessMessageHandler() {
            super(null, null);
        }

        private AccessRecord decodeForTest(byte[] payload) {
            return decode(payload);
        }
    }

    private static class TestableAirConditionMessageHandler extends AirConditionMessageHandler {

        private TestableAirConditionMessageHandler() {
            super(null, null);
        }

        private AirConditionRecord decodeForTest(byte[] payload) {
            return decode(payload);
        }
    }

    private static class TestableCircuitBreakMessageHandler extends CircuitBreakMessageHandler {

        private TestableCircuitBreakMessageHandler() {
            super(null, null);
        }

        private CircuitBreakRecord decodeForTest(byte[] payload) {
            return decode(payload);
        }
    }

    private static class TestableLightMessageHandler extends LightMessageHandler {

        private TestableLightMessageHandler() {
            super(null, null);
        }

        private LightRecord decodeForTest(byte[] payload) {
            return decode(payload);
        }
    }

    private static class TestableSensorMessageHandler extends SensorMessageHandler {

        private TestableSensorMessageHandler() {
            super(null, null);
        }

        private SensorRecord decodeForTest(byte[] payload) {
            return decode(payload);
        }
    }
}
