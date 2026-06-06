package xyz.jasenon.lab.mqtt.client.mqtt;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.jasenon.lab.common.DeviceType;
import xyz.jasenon.lab.common.command.CommandLine;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class MqttTaskTest {

    private static final Logger log = LoggerFactory.getLogger(MqttTaskTest.class);

    private static String hex(int i){
        return String.format("%02x",i);
    }

    @Test
    public void messageFormatObjectArrayTest(){
        int[] args = new int[]{1,2,3};
        Object[] objs = Arrays.stream(args).mapToObj(MqttTaskTest::hex).toArray();
        String commandLine = MessageFormat.format("{0} {1} {2}", objs);
        log.info("result:{}", commandLine);
        assertEquals("01 02 03", commandLine);
        byte[] payload = Arrays.stream(commandLine.split(" "))
                .mapToInt(hex -> Integer.parseInt(hex,16))
                .collect(ByteArrayOutputStream::new,
                        ByteArrayOutputStream::write,
                        (bos1,bos2)->{}).toByteArray();
        log.info("bytes:{}",payload);
        assertEquals("[1, 2, 3]", Arrays.toString(payload));
    }

    @Test
    public void mqttPayloadVerifier(){
        MqttTask task = new MqttTask("1");
        task.setArgs(new int[]{1});
        task.setDeviceId("1");
        task.setType(DeviceType.Access);
        task.setCommand(CommandLine.OPEN_ACCESS_ONCE);
        byte[] sendReq = task.convert().getPayload();
        log.info("bytes:{}",sendReq);
        assertTrue(MqttTask.Explainer.verifier(task.getCommand().getCommand().getCheckType(),sendReq));
    }

}
