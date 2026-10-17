package ymg.pwcca.privacydisplayplus;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Process-local identity; task numbers are never used to resolve a selection. */
final class TemporaryTasks {
    static final int FOLLOW = 0, ON = 1, OFF = 2;
    private final Map<Object, Entry> entries = new WeakHashMap<>();
    record Entry(String token, int mode, int override) {
        boolean protectedNow() { return override == ON; }
    }

    synchronized Entry offer(Object task, int mode) {
        Entry entry = entries.get(task);
        if (entry == null || entry.mode != mode) {
            entry = new Entry(UUID.randomUUID().toString(), mode, FOLLOW);
            entries.put(task, entry);
        }
        return entry;
    }

    synchronized Object resolve(String token) {
        for (var entry : entries.entrySet()) {
            if (entry.getValue().token.equals(token)) return entry.getKey();
        }
        return null;
    }

    synchronized void set(Object task, int mode, boolean enabled) {
        override(task, mode, enabled ? ON : FOLLOW);
    }

    synchronized void override(Object task, int mode, int value) {
        if (value < FOLLOW || value > OFF) throw new IllegalArgumentException("Invalid window override");
        Entry entry = offer(task, mode);
        entries.put(task, new Entry(entry.token, mode, value));
    }

    synchronized boolean protects(Object task, int currentMode) {
        return decision(task, currentMode) == ON;
    }

    synchronized int decision(Object task, int currentMode) {
        Entry entry = entries.get(task);
        if (entry == null) return FOLLOW;
        if (entry.mode != currentMode) { entries.remove(task); return FOLLOW; }
        return entry.override;
    }

    synchronized void remove(Object task) { entries.remove(task); }
    synchronized boolean known(Object task) { return entries.containsKey(task); }
    synchronized int count() { return (int) entries.values().stream().filter(Entry::protectedNow).count(); }
    synchronized int overrideCount() { return (int) entries.values().stream().filter(entry -> entry.override != FOLLOW).count(); }
}
