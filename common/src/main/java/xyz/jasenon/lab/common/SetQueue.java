package xyz.jasenon.lab.common;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class SetQueue<E> extends AbstractQueue<E> {
    private final Set<E> set;
    private final Queue<E> queue;

    public SetQueue(Set<E> set, Queue<E> queue){
        this.set = Objects.requireNonNull(set, "set must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    @Override
    public synchronized boolean offer(E e) {
        Objects.requireNonNull(e, "element must not be null");
        if (!set.add(e)) {
            return false;
        }

        boolean offered = queue.offer(e);
        if (!offered) {
            set.remove(e);
        }
        return offered;
    }

    /**
     * Put an active element that was taken by {@link #poll()} back into the queue.
     */
    public synchronized boolean returnToQueue(E e) {
        Objects.requireNonNull(e, "element must not be null");
        if (!set.contains(e)) {
            return false;
        }
        if (queue.contains(e)) {
            return false;
        }
        return queue.offer(e);
    }

    /**
     * Take the next queued element for work. The element stays active until
     * {@link #remove(Object)} is called, so duplicate offers are still rejected.
     */
    @Override
    public synchronized E poll() {
        return queue.poll();
    }

    @Override
    public synchronized E peek() {
        return queue.peek();
    }

    /**
     * Release an element from both the pending queue and the active set.
     */
    @Override
    public synchronized boolean remove(Object o) {
        boolean removed = queue.remove(o);
        boolean released = set.remove(o);
        return removed || released;
    }

    /**
     * Remove an element from both the underlying queue and the duplicate filter.
     */
    public synchronized boolean discard(Object o) {
        return remove(o);
    }

    @Override
    public synchronized boolean contains(Object o) {
        return set.contains(o);
    }

    public synchronized boolean isActive(Object o) {
        return set.contains(o);
    }

    @Override
    public synchronized Iterator<E> iterator() {
        return queue.stream().toList().iterator();
    }

    @Override
    public synchronized int size() {
        return queue.size();
    }

    public synchronized int reservedSize() {
        return set.size();
    }

    public synchronized int activeSize() {
        return set.size();
    }

    @Override
    public synchronized void clear() {
        queue.clear();
        set.clear();
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            changed |= offer(e);
        }
        return changed;
    }

}
