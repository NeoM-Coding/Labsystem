package xyz.jasenon.lab.mqtt.client.common;

import xyz.jasenon.lab.common.command.Task;

import java.util.concurrent.CompletableFuture;


public class PendingRequest<REQ extends Task> implements Cloneable {

    private CompletableFuture<Object> future;

    private REQ request;

    private Type type;

    private long timeout;

    private long interval;

    public enum Type {
        POLL,
        USER
    }

    public PendingRequest(REQ request, Type type, long timeout) {
        this.request = request;
        this.type = type;
        this.timeout = timeout;
        this.future = new CompletableFuture<>();
    }

    public PendingRequest(REQ request, Type type, long timeout,long interval){
        this.request = request;
        this.type = type;
        this.timeout = timeout;
        this.interval = interval;
        this.future = new CompletableFuture<>();
    }

    public CompletableFuture<Object> getFuture() {
        return future;
    }

    public REQ getRequest() {
        return request;
    }

    public Type getType() {
        return type;
    }

    public long getTimeout() {
        return timeout;
    }

    public long getInterval() { return interval; }

    public Poll<REQ> toPoll(){
        Poll<REQ> poll = new Poll<>(this.request, this.interval);
        poll.refresh();
        return poll;
    }

    @Override
    public PendingRequest<REQ> clone() {
        try {
            PendingRequest<REQ> copy = (PendingRequest<REQ>) super.clone();

            return new PendingRequest<>(
                    copy.request,
                    copy.type,
                    copy.timeout,
                    copy.interval
            );
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }


}
