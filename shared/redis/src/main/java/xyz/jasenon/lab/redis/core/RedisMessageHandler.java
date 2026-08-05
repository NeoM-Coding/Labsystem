package xyz.jasenon.lab.redis.core;

@FunctionalInterface
public interface RedisMessageHandler {

    void onMessage(String channel, String message);
}
