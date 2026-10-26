package xyz.jasenon.lab.mqtt.protocol.command.checker;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class CrcCheckerTests {
    @Test
    void matchesModbusReferenceVector() {
        byte[] input = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0x4B37, CrcChecker.calculateCRC16(input, input.length));
        assertArrayEquals(new byte[]{49, 50, 51, 52, 53, 54, 55, 56, 57, 0x37, 0x4B},
                CrcChecker.generatePayload(input));
    }

    @Test
    void verifiesReferencePayloadAndRejectsCorruption() {
        byte[] payload = {49, 50, 51, 52, 53, 54, 55, 56, 57, 0x37, 0x4B};
        assertTrue(CrcChecker.varify(payload));
        payload[0]++;
        assertFalse(CrcChecker.varify(payload));
    }

    @Test
    void emptyPayloadUsesInitialCrc() {
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF}, CrcChecker.generatePayload(new byte[0]));
    }

    @Test
    void rejectsPayloadWithoutTwoChecksumBytes() {
        assertThrows(IllegalArgumentException.class, () -> CrcChecker.varify(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> CrcChecker.varify(new byte[]{1}));
    }
}
