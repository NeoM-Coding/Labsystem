package xyz.jasenon.lab.mqtt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.command.checker.CrcChecker;
import xyz.jasenon.lab.common.command.checker.SumChecker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CheckerTests {

    private static final Logger log = LoggerFactory.getLogger(CheckerTests.class);

    @Test
    public void signSum(){
        log.info("array:{}", SumChecker.calculateCheckSum(new byte[]{31,1,1,2,3,25,0,0}));
    }

    @Test
    public void signSumVerify(){
        assertTrue(SumChecker.verifyCheckSum(new byte[]{31,1,1,2,3, (byte) 255, (byte) 255, (byte) 255,35}));
    }

    @Test
    public void unsignSum(){
        log.info("array:{}", SumChecker.calculateUnsignedByteCheckSum(new byte[]{41,10,1,0,17,0,}));
    }

    @Test
    public void crc(){
        log.info("array:{}", CrcChecker.generatePayload(new byte[]{}));
    }

}
