package xyz.jasenon.lab.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexedQueueTests {

    @Test
    void activeQueueRejectsDuplicateWhilePolledElementIsActive() {
        ActiveQueue<String> queue = new ActiveQueue<>(new HashSet<>(), new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("task-1"));
        assertEquals("task-1", queue.poll());
        assertTrue(queue.contains("task-1"));
        assertTrue(queue.isActive("task-1"));
        assertEquals(1, queue.activeSize());

        assertFalse(queue.offer("task-1"));
        assertNull(queue.poll());

        assertTrue(queue.returnToQueue("task-1"));
        assertFalse(queue.returnToQueue("task-1"));
        assertEquals("task-1", queue.poll());
    }

    @Test
    void activeQueueRemoveReleasesQueuedElementForRefresh() {
        ActiveQueue<String> queue = new ActiveQueue<>(new HashSet<>(), new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("task-1"));
        assertTrue(queue.remove("task-1"));
        assertTrue(queue.offer("task-1"));

        assertEquals("task-1", queue.poll());
    }

    @Test
    void activeQueueRemoveReleasesPolledElementAndPreventsReturn() {
        ActiveQueue<String> queue = new ActiveQueue<>(new HashSet<>(), new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("task-1"));
        assertEquals("task-1", queue.poll());

        assertTrue(queue.remove("task-1"));
        assertFalse(queue.contains("task-1"));
        assertFalse(queue.isActive("task-1"));
        assertFalse(queue.returnToQueue("task-1"));
        assertTrue(queue.offer("task-1"));
        assertEquals("task-1", queue.poll());
    }

    @Test
    void uniqueQueueReleasesIndexWhenElementIsPolled() {
        UniqueQueue<String> queue = new UniqueQueue<>(new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("runtime-1"));
        assertFalse(queue.offer("runtime-1"));
        assertTrue(queue.contains("runtime-1"));

        assertEquals("runtime-1", queue.poll());
        assertFalse(queue.contains("runtime-1"));
        assertEquals(0, queue.indexedSize());

        assertTrue(queue.offer("runtime-1"));
    }
}
