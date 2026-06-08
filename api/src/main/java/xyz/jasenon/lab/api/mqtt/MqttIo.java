package xyz.jasenon.lab.api.mqtt;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface MqttIo {

    Object syncSend(MqttTaskDto task) throws ExecutionException, InterruptedException, TimeoutException;

    CompletableFuture<Object> asyncSend(MqttTaskDto task);
}
