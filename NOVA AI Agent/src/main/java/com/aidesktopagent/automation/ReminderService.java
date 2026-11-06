package com.aidesktopagent.automation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Simple in-memory reminders (Day 33). */
public final class ReminderService {

    public record Reminder(String id, String message, Instant dueAt, boolean dismissed) {
    }

    private final CopyOnWriteArrayList<Reminder> reminders = new CopyOnWriteArrayList<>();

    public Reminder add(String message, Instant dueAt) {
        Reminder r = new Reminder(UUID.randomUUID().toString(), message, dueAt, false);
        reminders.add(r);
        return r;
    }

    public boolean dismiss(String id) {
        for (int i = 0; i < reminders.size(); i++) {
            Reminder r = reminders.get(i);
            if (r.id().equals(id)) {
                reminders.set(i, new Reminder(r.id(), r.message(), r.dueAt(), true));
                return true;
            }
        }
        return false;
    }

    public List<Reminder> dueNow() {
        Instant now = Instant.now();
        List<Reminder> due = new ArrayList<>();
        for (Reminder r : reminders) {
            if (!r.dismissed() && !r.dueAt().isAfter(now)) {
                due.add(r);
            }
        }
        return due;
    }

    public List<Reminder> all() {
        return List.copyOf(reminders);
    }
}
