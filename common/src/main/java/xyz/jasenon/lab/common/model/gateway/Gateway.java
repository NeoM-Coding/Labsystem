package xyz.jasenon.lab.common.model.gateway;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import xyz.jasenon.lab.common.model.BaseEntity;
import xyz.jasenon.lab.common.model.device.devices.*;
import xyz.jasenon.lab.common.model.gateway.gateways.RS485Gateway;
import xyz.jasenon.lab.common.model.gateway.gateways.SocketGateway;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "gatewayType", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RS485Gateway.class, name = "RS485"),
        @JsonSubTypes.Type(value = SocketGateway.class, name = "Socket")
})
public class Gateway extends BaseEntity {
    // 网关名称
    private String gatewayName;
    // 网关作用的实验室id
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> usingIn;
    // 网关的类型
    protected GatewayType gatewayType;

    public Gateway(String gatewayName, List<String> usingIn, GatewayType gatewayType) {
        this.gatewayName = gatewayName;
        this.usingIn = usingIn;
        this.gatewayType = gatewayType;
    }

    public Gateway() {
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public void setGatewayName(String gatewayName) {
        this.gatewayName = gatewayName;
    }

    public List<String> getUsingIn() {
        return usingIn;
    }

    public void setUsingIn(List<String> usingIn) {
        this.usingIn = usingIn;
    }

    public GatewayType getGatewayType() {
        return gatewayType;
    }

    public void setGatewayType(GatewayType gatewayType) {
        this.gatewayType = gatewayType;
    }
}
