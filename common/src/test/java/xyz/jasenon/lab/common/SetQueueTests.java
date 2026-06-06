package xyz.jasenon.lab.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetQueueTests {

    @Test
    void rejectsDuplicateWhilePolledElementIsReserved() {
        SetQueue<String> queue = new SetQueue<>(new HashSet<>(), new ArrayDeque<>());

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
    void removeReleasesQueuedElementForRefresh() {
        SetQueue<String> queue = new SetQueue<>(new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("task-1"));
        assertTrue(queue.remove("task-1"));
        assertTrue(queue.offer("task-1"));

        assertEquals("task-1", queue.poll());
    }

    @Test
    void removeReleasesPolledElementReservationAndPreventsReturn() {
        SetQueue<String> queue = new SetQueue<>(new HashSet<>(), new ArrayDeque<>());

        assertTrue(queue.offer("task-1"));
        assertEquals("task-1", queue.poll());

        assertTrue(queue.remove("task-1"));
        assertFalse(queue.contains("task-1"));
        assertFalse(queue.isActive("task-1"));
        assertFalse(queue.returnToQueue("task-1"));
        assertTrue(queue.offer("task-1"));
        assertEquals("task-1", queue.poll());
    }
}
