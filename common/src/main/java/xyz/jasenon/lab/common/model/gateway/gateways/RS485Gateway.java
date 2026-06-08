package xyz.jasenon.lab.common.model.gateway.gateways;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import xyz.jasenon.lab.common.model.gateway.Gateway;
import xyz.jasenon.lab.common.model.gateway.GatewayType;

import java.util.List;
import java.util.Objects;

@Getter
@Setter
@TableName(value = "gateway",autoResultMap = true)
public class RS485Gateway extends Gateway {
    // 发送主题
    private String sendTopic;
    // 接收主题
    private String acceptTopic;

    @Override
    public int hashCode(){
        return Objects.hash(sendTopic, acceptTopic);
    }

    @Override
    public boolean equals(Object obj){
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RS485Gateway other = (RS485Gateway) obj;
        return Objects.equals(sendTopic, other.sendTopic)
                && Objects.equals(acceptTopic, other.acceptTopic);
    }

    public RS485Gateway(String gatewayName, List<String> usingIn, GatewayType type, String sendTopic, String acceptTopic) {
        super(gatewayName, usingIn, type);
        this.sendTopic = sendTopic;
        this.acceptTopic = acceptTopic;
    }

    public RS485Gateway(String sendTopic, String acceptTopic) {
        this.sendTopic = sendTopic;
        this.acceptTopic = acceptTopic;
    }

    public RS485Gateway(){
        this.gatewayType = GatewayType.RS485;
    }
}
