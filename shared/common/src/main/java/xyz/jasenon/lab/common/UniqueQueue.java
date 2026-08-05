package xyz.jasenon.lab.common;

import java.util.Collection;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class UniqueQueue<E> extends AbstractIndexedQueue<E> {

    private final Queue<E> queue;

    public UniqueQueue(Set<E> queuedIndex, Queue<E> queue) {
        super(queuedIndex);
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    @Override
    protected boolean enqueue(E e) {
        return queue.offer(e);
    }

    @Override
    protected E dequeue() {
        return queue.poll();
    }

    @Override
    protected E peekQueued() {
        return queue.peek();
    }

    @Override
    protected boolean removeQueued(Object o) {
        return queue.remove(o);
    }

    @Override
    protected Collection<E> snapshotQueued() {
        return queue.stream().toList();
    }

    @Override
    protected int queuedSize() {
        return queue.size();
    }

    @Override
    protected void clearQueued() {
        queue.clear();
    }
}
