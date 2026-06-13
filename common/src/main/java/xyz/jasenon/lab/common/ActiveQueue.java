package xyz.jasenon.lab.common;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class ActiveQueue<E> extends AbstractIndexedQueue<E> {

    private final Set<E> activeIndex;
    private final Queue<E> queue;

    public ActiveQueue(Set<E> activeIndex, Set<E> queuedIndex, Queue<E> queue) {
        super(queuedIndex);
        this.activeIndex = Objects.requireNonNull(activeIndex, "activeIndex must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    @Override
    public synchronized boolean offer(E e) {
        Objects.requireNonNull(e, "element must not be null");
        if (!activeIndex.add(e)) {
            return false;
        }
        if (!queuedIndex.add(e)) {
            activeIndex.remove(e);
            return false;
        }

        boolean offered = enqueue(e);
        if (!offered) {
            queuedIndex.remove(e);
            activeIndex.remove(e);
        }
        return offered;
    }

    public synchronized boolean returnToQueue(E e) {
        Objects.requireNonNull(e, "element must not be null");
        if (!activeIndex.contains(e)) {
            return false;
        }
        if (!queuedIndex.add(e)) {
            return false;
        }

        boolean offered = enqueue(e);
        if (!offered) {
            queuedIndex.remove(e);
        }
        return offered;
    }

    @Override
    public synchronized boolean remove(Object o) {
        boolean removedFromQueue = removeQueued(o);
        boolean removedFromQueueIndex = queuedIndex.remove(o);
        boolean removedFromActiveIndex = activeIndex.remove(o);
        return removedFromQueue || removedFromQueueIndex || removedFromActiveIndex;
    }

    @Override
    public synchronized boolean contains(Object o) {
        return activeIndex.contains(o);
    }

    public synchronized boolean isActive(Object o) {
        return activeIndex.contains(o);
    }

    public synchronized int activeSize() {
        return activeIndex.size();
    }

    public synchronized Set<E> activeSnapshot() {
        return new HashSet<>(activeIndex);
    }

    @Override
    public synchronized void clear() {
        super.clear();
        activeIndex.clear();
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
