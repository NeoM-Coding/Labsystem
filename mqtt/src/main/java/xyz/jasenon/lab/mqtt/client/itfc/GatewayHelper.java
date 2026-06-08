package xyz.jasenon.lab.mqtt.client.itfc;

import xyz.jasenon.lab.common.model.gateway.Gateway;
import xyz.jasenon.lab.common.model.gateway.gateways.RS485Gateway;

import java.util.List;

public interface GatewayHelper {

    List<RS485Gateway> listAll();

    interface CRUD {
        boolean addRS485Gateway();

        boolean updateRS485Gateway(String gatewayId, RS485Gateway gateway);

        boolean remobeRS485Gateway(String gatewayId);
    }

    /*
     * 为集群考虑  根据当前机器的workid以及所有works  获取只归属自己管理的gateway
     * 后续实现  暂时不考虑集群问题
     */
    List<Gateway> listRange(int workerId, int works);

}
