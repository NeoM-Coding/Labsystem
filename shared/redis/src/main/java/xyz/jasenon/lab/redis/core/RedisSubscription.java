package xyz.jasenon.lab.redis.core;

import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.CompletableFuture;

public final class RedisSubscription implements AutoCloseable {

    private final JedisPubSub subscriber;
    private final CompletableFuture<Void> task;

    RedisSubscription(JedisPubSub subscriber, CompletableFuture<Void> task) {
        this.subscriber = subscriber;
        this.task = task;
    }

    public boolean isRunning() {
        return !task.isDone();
    }

    @Override
    public void close() {
        subscriber.unsubscribe();
        task.cancel(true);
    }
}
