package com.aidesktopagent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {

    @Test
    void priorityOrder() {
        TaskQueue q = new TaskQueue();
        q.enqueue("low", TaskQueue.Priority.LOW);
        q.enqueue("high", TaskQueue.Priority.HIGH);
        q.enqueue("normal", TaskQueue.Priority.NORMAL);
        assertEquals("high", q.pollNext().getCommand());
        assertEquals("normal", q.pollNext().getCommand());
        assertEquals("low", q.pollNext().getCommand());
    }

    @Test
    void cancelQueued() {
        TaskQueue q = new TaskQueue();
        var t = q.enqueue("x", TaskQueue.Priority.NORMAL);
        assertTrue(q.cancel(t.getId()));
        assertEquals(TaskQueue.TaskStatus.CANCELLED, t.getStatus());
        assertNull(q.pollNext());
    }
}
