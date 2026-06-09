package xyz.jasenon.lab.api.mqtt.dto;

import java.io.Serializable;
import java.util.Arrays;

public class MqttResponseDto implements Serializable {

    private String gatewayId;
    private int[] payload;

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public int[] getPayload() {
        return payload;
    }

    public void setPayload(int[] payload) {
        this.payload = payload;
    }

    public static MqttResponseDto of(String gatewayId, byte[] payload) {
        MqttResponseDto dto = new MqttResponseDto();
        dto.setGatewayId(gatewayId);
        dto.setPayload(toUnsignedIntArray(payload));
        return dto;
    }

    private static int[] toUnsignedIntArray(byte[] payload) {
        if (payload == null) {
            return new int[0];
        }
        return Arrays.stream(toBoxed(payload)).mapToInt(Byte::toUnsignedInt).toArray();
    }

    private static Byte[] toBoxed(byte[] payload) {
        Byte[] boxed = new Byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            boxed[i] = payload[i];
        }
        return boxed;
    }
}
