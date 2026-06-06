package xyz.jasenon.lab.mqtt.client;

import cn.hutool.core.lang.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.common.exception.BusinessException;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.itfc.TaskHelper;
import xyz.jasenon.lab.mqtt.client.mqtt.MqttTask;

import java.util.Map;
import java.util.concurrent.*;

@Component
public class SysClientMananger {

    private final static Map<String, AbstractSysClient<? extends Task>> clients = new ConcurrentHashMap<>();
    private final TaskHelper helper;

    public SysClientMananger(TaskHelper helper) {
        this.helper = helper;
    }

    public Object syncSend(MqttTask.Dto dto) throws ExecutionException, InterruptedException, TimeoutException {
        MqttTask userTask = helper.help(dto);
        var client = (AbstractSysClient<MqttTask>) clients.get(userTask.getGatewayId());
        if (client != null){
            userTask.convert();
            PendingRequest<MqttTask> task = userTask.decorat();
            client.offer(task);
            return task.getFuture().get(task.getTimeout(), TimeUnit.MILLISECONDS);
        }
        throw new BusinessException(HttpStatus.NOT_FOUND.value(),"gateway doesn't exist!");
    }

    public static void remove(AbstractSysClient<? extends Task> client) {
        if (client == null) {
            return;
        }
        clients.remove(client.gatewayId, client);
    }

    public static void register(AbstractSysClient<? extends Task> client)  {
        if (client == null) {
            return;
        }
        clients.put(client.gatewayId,client);
    }

    /**
     * 借助GatewayHelper 提供的能力list all gatewayId
     * 遍历clients entryset 检查缺失了哪个 gateway
     * 由watchdog 重新拉起他  并使用slf4j 记录warn
     * gateway base 应当提供一个version标记gateway是否更新需要重新替换
     */
    private void watchdog(){
        while(true){




        }
    }



}
