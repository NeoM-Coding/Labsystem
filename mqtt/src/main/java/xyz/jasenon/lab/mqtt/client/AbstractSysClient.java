package xyz.jasenon.lab.mqtt.client;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import xyz.jasenon.lab.common.SetQueue;
import xyz.jasenon.lab.common.command.Task;
import xyz.jasenon.lab.mqtt.client.common.PendingRequest;
import xyz.jasenon.lab.mqtt.client.common.Poll;

import java.util.concurrent.*;

public abstract class AbstractSysClient<REQ extends Task> extends MqttClient {
    public final String gatewayId;

    private final BlockingQueue<PendingRequest<REQ>> userQueue = new LinkedBlockingQueue<>();
    private final SetQueue<Poll<REQ>> pollQueue = new SetQueue<>(ConcurrentHashMap.newKeySet(), new DelayQueue<>());
    private final Thread worker;
    private volatile PendingRequest<REQ> current;

    public AbstractSysClient(String serverURI, String clientId, String gatewayId) throws MqttException {
        super(serverURI, clientId, new MemoryPersistence());
        this.gatewayId = gatewayId;
        worker = new Thread(this::loop);
        worker.setName("gateway-worker:" + gatewayId);
        worker.setDaemon(true);
        worker.start();
    }

    private void loop() {
        while (true) {
            try {
                PendingRequest<REQ> pending = next();
                execute(pending);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private PendingRequest<REQ> next() throws InterruptedException {
        while(true){
            PendingRequest<REQ> userReq = userQueue.poll();
            if (userReq != null){
                return userReq;
            }

            Poll<REQ> poll = pollQueue.poll();
            if (poll != null){
                return poll.poll();
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    private void execute(PendingRequest<REQ> request) {
        this.current = request;

        try {
            send(request.getRequest());
            Object result = request.getFuture().get(request.getTimeout(), TimeUnit.MILLISECONDS);
            onResponse(result);
        } catch (TimeoutException e) {
            request.getFuture().completeExceptionally(e);
            onTimeout(request.getRequest(), e);
        } catch (Exception e) {
            request.getFuture().completeExceptionally(e);
            onError(request.getRequest(), e);
        } finally {
            current = null;

            if (request.getType() == PendingRequest.Type.POLL){
                pollQueue.returnToQueue(request.toPoll());
            }
        }
    }

    public void recevive(Task resp){
        PendingRequest<REQ> pending = current;
        if (pending != null && match(pending.getRequest(), resp)){
            pending.getFuture().complete(resp);
        }
        onMessage(resp);
    }

    // 发送逻辑
    protected abstract void send(REQ req);

    // 匹配
    protected abstract <RESP extends Task> boolean match(REQ req, RESP resp);

    // 消息到达
    protected abstract void onMessage(Object resp);

    protected abstract void onResponse(Object resp);

    protected abstract void onTimeout(REQ req, TimeoutException e);

    protected abstract void onError(REQ req, Exception e);

    protected PendingRequest.Type currentType() {
        PendingRequest<REQ> pending = current;
        return pending == null ? null : pending.getType();
    }

    protected boolean offer(PendingRequest<REQ> req) {
        return userQueue.offer(req);
    }

    protected boolean offer(Poll<REQ> req) {
        return pollQueue.offer(req);
    }

    protected boolean remove(Poll<REQ> req) {
        return pollQueue.remove(req);
    }

}
