package xyz.jasenon.lab.mqtt.protocol.command.checker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SumCheckerTests {
    @Test
    void calculatesSignedChecksumIncludingNegativeBytes() {
        assertEquals((byte) 63, SumChecker.calculateCheckSum(new byte[]{31, 1, 1, 2, 3, 25, 0, 0}));
        assertEquals((byte) 35, SumChecker.calculateCheckSum(new byte[]{31, 1, 1, 2, 3, -1, -1, -1}));
    }

    @Test
    void calculatesUnsignedChecksumAndWrapsAt255() {
        assertEquals((byte) 69, SumChecker.calculateUnsignedByteCheckSum(new byte[]{41, 10, 1, 0, 17, 0}));
        assertEquals((byte) 1, SumChecker.calculateUnsignedByteCheckSum(new byte[]{(byte) 254, 2}));
    }

    @Test
    void verifiesSignedPayloadAndRejectsCorruption() {
        byte[] input = {31, 1, 1, 2, 3, -1, -1, -1};
        byte[] payload = SumChecker.generateSgPayload(input);
        assertArrayEquals(new byte[]{31, 1, 1, 2, 3, -1, -1, -1, 35}, payload);
        assertTrue(SumChecker.verifyCheckSum(payload));
        payload[0]++;
        assertFalse(SumChecker.verifyCheckSum(payload));
        assertEquals(31, input[0]);
    }

    @Test
    void verifiesUnsignedPayloadAndRejectsCorruption() {
        byte[] payload = SumChecker.generateUnSgPayload(new byte[]{(byte) 254, 2});
        assertArrayEquals(new byte[]{(byte) 254, 2, 1}, payload);
        assertTrue(SumChecker.verifyUnsignedByteCheckSum(payload));
        payload[2]++;
        assertFalse(SumChecker.verifyUnsignedByteCheckSum(payload));
    }

    @Test
    void emptyPayloadHasZeroChecksum() {
        assertArrayEquals(new byte[]{0}, SumChecker.generateSgPayload(new byte[0]));
        assertArrayEquals(new byte[]{0}, SumChecker.generateUnSgPayload(new byte[0]));
    }
}
