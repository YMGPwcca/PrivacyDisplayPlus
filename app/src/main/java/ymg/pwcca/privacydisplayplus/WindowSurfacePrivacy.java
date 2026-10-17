package ymg.pwcca.privacydisplayplus;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.LongSupplier;
import java.lang.ref.WeakReference;

/** Queues bounded regions on existing app buffers; creates no layers or input windows. */
final class WindowSurfacePrivacy {
    private final PolicyAccess access;
    private final Map<Object, Entry> owned = new WeakHashMap<>();
    private final Map<Object, DragLease> dragging = new WeakHashMap<>();
    private final LongSupplier clock;
    private static final long DRAG_LEASE_MS = 3000;

    WindowSurfacePrivacy(PolicyAccess access) { this(access, () -> System.nanoTime() / 1_000_000); }
    WindowSurfacePrivacy(PolicyAccess access, LongSupplier clock) { this.access = access; this.clock = clock; }

    synchronized boolean enable(Object window, Object task, Object surface) throws ReflectiveOperationException {
        return enable(window, task, surface, null);
    }

    synchronized boolean enable(Object window, Object task, Object surface, String selectedPackage) throws ReflectiveOperationException {
        SplitPrivacyRegion region = access.privacyRegion(window);
        if (region == null) return false;
        int mode = access.windowingMode(task);
        Entry previous = owned.get(window);
        clearReplaced(window, previous, surface);
        // Queued before the router removes Samsung's full-app reason.
        if (!sameOwner(previous, task, mode) || !sameRegion(previous, surface, region)) {
            access.queuePrivacyRegion(window, surface, region);
        }
        owned.put(window, new Entry(new WeakReference<>(task), surface, selectedPackage, mode, region));
        return true;
    }

    private void clearReplaced(Object window, Entry previous, Object surface) throws ReflectiveOperationException {
        if (previous != null && previous.surface != surface) clear(window, previous);
    }

    private static boolean sameOwner(Entry entry, Object task, int mode) {
        return entry != null && entry.task.get() == task && entry.windowingMode == mode;
    }

    synchronized void prepare(Object window, ProtectionOptions options) throws ReflectiveOperationException {
        Entry entry = owned.get(window);
        if (entry == null) return;
        options = optionsFor(window, entry, options);
        if (!keep(window, entry, options)) {
            clear(window, entry);
            return;
        }
        SplitPrivacyRegion region = access.privacyRegion(window);
        if (region == null) clear(window, entry);
        else if (!sameRegion(entry, entry.surface, region)) {
            access.queuePrivacyRegion(window, entry.surface, region);
            owned.put(window, new Entry(entry.task, entry.surface, entry.selectedPackage, entry.windowingMode, region));
        }
    }

    private boolean sameRegion(Entry entry, Object surface, SplitPrivacyRegion region) {
        // Shell owns live floating movement. Rewriting an unchanged WMS frame would undo its clip.
        if (!floatingSurface(entry, surface)) return false;
        return liveDrag(entry.task.get()) && Arrays.deepEquals(entry.region.data, region.data);
    }

    private static boolean floatingSurface(Entry entry, Object surface) {
        return entry != null && entry.windowingMode == 5 && entry.surface == surface;
    }
    private boolean liveDrag(Object task) {
        DragLease lease = dragging.get(task);
        return lease != null && clock.getAsLong() < lease.until;
    }

    synchronized void beginDrag(Object task, String id) { dragging.put(task, new DragLease(id, clock.getAsLong() + DRAG_LEASE_MS)); }
    synchronized void renewDrag(Object task, String id) {
        if (sameDrag(task, id)) beginDrag(task, id);
    }
    synchronized void endDrag(Object task, String id) {
        if (sameDrag(task, id)) dragging.remove(task);
    }
    private boolean sameDrag(Object task, String id) {
        DragLease lease = dragging.get(task);
        return lease != null && lease.id.equals(id);
    }
    synchronized void endDrag(Object task) { dragging.remove(task); }

    synchronized List<DragTarget> dragTargets(Object task, ProtectionOptions options) throws ReflectiveOperationException {
        List<DragTarget> result = new ArrayList<>();
        for (var item : owned.entrySet()) {
            DragTarget target = dragTarget(item.getKey(), item.getValue(), task, options);
            if (target == null) continue;
            result.add(target);
            if (result.size() == 8) break;
        }
        return result;
    }

    private DragTarget dragTarget(Object window, Entry entry, Object task, ProtectionOptions options)
            throws ReflectiveOperationException {
        if (entry.task.get() != task || entry.windowingMode != 5) return null;
        if (!keep(window, entry, optionsFor(window, entry, options))) return null;
        return new DragTarget(entry.surface, access.windowBounds(window), access.displayBounds(window));
    }

    private ProtectionOptions optionsFor(Object window, Entry entry, ProtectionOptions options)
            throws ReflectiveOperationException {
        Object task = entry.task.get();
        if (task == null) return options;
        String name = entry.selectedPackage != null ? entry.selectedPackage : access.windowPackage(window);
        return options.forApp(name, access.userId(task));
    }

    private boolean keep(Object window, Entry entry, ProtectionOptions options)
            throws ReflectiveOperationException {
        if (!options.allows(entry.windowingMode)) return false;
        Object task = entry.task.get();
        if (task == null || access.task(window) != task) return false;
        if (!sameSurface(window, entry.surface)) return false;
        return protectedTask(task, entry.windowingMode);
    }

    private boolean sameSurface(Object window, Object surface) throws ReflectiveOperationException {
        return access.displayId(window) == 0 && access.bufferSurface(window) == surface
                && access.validSurface(surface);
    }

    private boolean protectedTask(Object task, int mode) throws ReflectiveOperationException {
        if (access.windowingMode(task) != mode) return false;
        if (mode == 6 && (!access.splitScreen(task) || !access.leafTask(task))) return false;
        // Samsung has finalized custom-mode, screen/power suppression and task requests.
        return access.taskPrivacyRequested(task);
    }

    private void clear(Object window, Entry entry) throws ReflectiveOperationException {
        if (access.validSurface(entry.surface)) access.queuePrivacyRegion(window, entry.surface, null);
        owned.remove(window); // Failed transactions retain ownership for a later cleanup attempt.
    }

    record DragTarget(Object surface, SplitPrivacyRegion.Bounds window, SplitPrivacyRegion.Bounds display) {}
    private record DragLease(String id, long until) {}
    private record Entry(WeakReference<Object> task, Object surface, String selectedPackage, int windowingMode,
                         SplitPrivacyRegion region) {}
}
