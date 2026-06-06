package xyz.jasenon.lab.common.command;

import java.util.Arrays;
import java.util.Objects;

public class Task {

    // 网关id
    private String gatewayId;

    // 负载
    private byte[] payload;

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    // 重写hashcode
    @Override
    public int hashCode(){
        return Objects.hash(gatewayId, Arrays.hashCode(payload));
    }

    // 重写equals
    @Override
    public boolean equals(Object obj){
        if(this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Task task = (Task) obj;
        return Objects.equals(gatewayId, task.gatewayId)
                && Arrays.equals(payload, task.payload);
    }

    public Task(String gatewayId, byte[] payload) {
        this.gatewayId = gatewayId;
        this.payload = payload;
    }
}
