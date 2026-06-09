package xyz.jasenon.lab.api.mqtt;

import org.apache.dubbo.config.annotation.DubboService;
import xyz.jasenon.lab.api.mqtt.dto.MqttResponseDto;
import xyz.jasenon.lab.api.mqtt.dto.MqttTaskDto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface MqttIo {

    MqttResponseDto syncSend(MqttTaskDto task) throws ExecutionException, InterruptedException, TimeoutException;

    CompletableFuture<MqttResponseDto> asyncSend(MqttTaskDto task);
}
