package ymg.pwcca.privacydisplayplus;

import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Queues bounded regions on existing app buffers; creates no layers or input windows. */
final class SplitSurfacePrivacy {
    private final PolicyAccess access;
    private final Map<Object, Entry> owned = new WeakHashMap<>();

    SplitSurfacePrivacy(PolicyAccess access) { this.access = access; }

    synchronized boolean enable(Object window, Object task, Object surface) throws ReflectiveOperationException {
        return enable(window, task, surface, null);
    }

    synchronized boolean enable(Object window, Object task, Object surface, String selectedPackage) throws ReflectiveOperationException {
        SplitPrivacyRegion region = access.privacyRegion(window);
        if (region == null) return false;
        Entry previous = owned.get(window);
        if (previous != null && previous.surface != surface) clear(window, previous);
        // Queued before the router removes Samsung's full-app reason.
        access.queuePrivacyRegion(window, surface, region);
        owned.put(window, new Entry(new WeakReference<>(task), surface, selectedPackage));
        return true;
    }

    synchronized void prepare(Object window, ProtectionOptions options) throws ReflectiveOperationException {
        Entry entry = owned.get(window);
        if (entry == null) return;
        Object task = entry.task.get();
        if (task != null) {
            String name = entry.selectedPackage != null ? entry.selectedPackage : access.windowPackage(window);
            options = options.forApp(name, access.userId(task));
        }
        if (!keep(window, entry, options)) {
            clear(window, entry);
            return;
        }
        SplitPrivacyRegion region = access.privacyRegion(window);
        if (region != null) access.queuePrivacyRegion(window, entry.surface, region);
    }

    private boolean keep(Object window, Entry entry, ProtectionOptions options)
            throws ReflectiveOperationException {
        if (!options.split) return false;
        Object task = entry.task.get();
        if (task == null || access.task(window) != task) return false;
        if (access.bufferSurface(window) != entry.surface) return false;
        if (!access.validSurface(entry.surface)) return false;
        return access.displayId(window) == 0 && protectedSplitTask(task);
    }

    private boolean protectedSplitTask(Object task) throws ReflectiveOperationException {
        if (access.windowingMode(task) != 6) return false;
        if (!access.splitScreen(task) || !access.leafTask(task)) return false;
        // Samsung has finalized custom-mode, screen/power suppression and task requests.
        return access.taskPrivacyRequested(task);
    }

    private void clear(Object window, Entry entry) throws ReflectiveOperationException {
        if (access.validSurface(entry.surface)) access.queuePrivacyRegion(window, entry.surface, null);
        owned.remove(window); // Failed transactions retain ownership for a later cleanup attempt.
    }

    private record Entry(WeakReference<Object> task, Object surface, String selectedPackage) {}
}
