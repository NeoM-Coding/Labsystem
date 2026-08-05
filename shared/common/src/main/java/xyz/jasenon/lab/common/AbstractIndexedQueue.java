package xyz.jasenon.lab.common;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public abstract class AbstractIndexedQueue<E> extends AbstractQueue<E> {

    protected final Set<E> queuedIndex;

    protected AbstractIndexedQueue(Set<E> queuedIndex) {
        this.queuedIndex = Objects.requireNonNull(queuedIndex, "queuedIndex must not be null");
    }

    @Override
    public synchronized boolean offer(E e) {
        Objects.requireNonNull(e, "element must not be null");
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
    public synchronized E poll() {
        E e = dequeue();
        if (e != null) {
            queuedIndex.remove(e);
            afterPoll(e);
        }
        return e;
    }

    @Override
    public synchronized E peek() {
        return peekQueued();
    }

    @Override
    public synchronized boolean remove(Object o) {
        boolean removedFromQueue = removeQueued(o);
        boolean removedFromIndex = queuedIndex.remove(o);
        return removedFromQueue || removedFromIndex;
    }

    public synchronized boolean discard(Object o) {
        return remove(o);
    }

    @Override
    public synchronized boolean contains(Object o) {
        return queuedIndex.contains(o);
    }

    public synchronized int indexedSize() {
        return queuedIndex.size();
    }

    @Override
    public synchronized Iterator<E> iterator() {
        return snapshotQueued().iterator();
    }

    @Override
    public synchronized int size() {
        return queuedSize();
    }

    @Override
    public synchronized void clear() {
        clearQueued();
        queuedIndex.clear();
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> c) {
        boolean changed = false;
        for (E e : c) {
            changed |= offer(e);
        }
        return changed;
    }

    protected void afterPoll(E e) {
    }

    protected abstract boolean enqueue(E e);

    protected abstract E dequeue();

    protected abstract E peekQueued();

    protected abstract boolean removeQueued(Object o);

    protected abstract Collection<E> snapshotQueued();

    protected abstract int queuedSize();

    protected abstract void clearQueued();
}
