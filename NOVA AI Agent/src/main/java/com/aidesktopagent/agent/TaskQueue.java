package com.aidesktopagent.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple priority-aware task queue (Day 19).
 */
public final class TaskQueue {

    public enum Priority {
        LOW, NORMAL, HIGH
    }

    public enum TaskStatus {
        QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public static final class Task {
        private final String id = UUID.randomUUID().toString();
        private final String command;
        private final Priority priority;
        private volatile TaskStatus status = TaskStatus.QUEUED;
        private volatile String resultMessage;

        public Task(String command, Priority priority) {
            this.command = command;
            this.priority = priority != null ? priority : Priority.NORMAL;
        }

        public String getId() {
            return id;
        }

        public String getCommand() {
            return command;
        }

        public Priority getPriority() {
            return priority;
        }

        public TaskStatus getStatus() {
            return status;
        }

        public String getResultMessage() {
            return resultMessage;
        }

        void setStatus(TaskStatus s) {
            this.status = s;
        }

        void setResultMessage(String m) {
            this.resultMessage = m;
        }
    }

    private final BlockingQueue<Task> high = new LinkedBlockingQueue<>();
    private final BlockingQueue<Task> normal = new LinkedBlockingQueue<>();
    private final BlockingQueue<Task> low = new LinkedBlockingQueue<>();
    private final AtomicReference<Task> running = new AtomicReference<>();
    private final List<Task> history = new ArrayList<>();

    public Task enqueue(String command, Priority priority) {
        Task t = new Task(command, priority);
        switch (t.getPriority()) {
            case HIGH -> high.offer(t);
            case LOW -> low.offer(t);
            default -> normal.offer(t);
        }
        synchronized (history) {
            history.add(t);
        }
        return t;
    }

    public Task pollNext() {
        Task t = high.poll();
        if (t == null) {
            t = normal.poll();
        }
        if (t == null) {
            t = low.poll();
        }
        if (t != null) {
            t.setStatus(TaskStatus.RUNNING);
            running.set(t);
        }
        return t;
    }

    public void complete(Task t, boolean success, String message) {
        if (t == null) {
            return;
        }
        t.setStatus(success ? TaskStatus.COMPLETED : TaskStatus.FAILED);
        t.setResultMessage(message);
        running.compareAndSet(t, null);
    }

    public boolean cancel(String taskId) {
        synchronized (history) {
            for (Task t : history) {
                if (t.getId().equals(taskId)) {
                    if (t.getStatus() == TaskStatus.QUEUED) {
                        high.remove(t);
                        normal.remove(t);
                        low.remove(t);
                        t.setStatus(TaskStatus.CANCELLED);
                        return true;
                    }
                    if (t.getStatus() == TaskStatus.RUNNING) {
                        t.setStatus(TaskStatus.CANCELLED);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Task getRunning() {
        return running.get();
    }

    public int queuedCount() {
        return high.size() + normal.size() + low.size();
    }

    public List<Task> snapshotHistory() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }
}
