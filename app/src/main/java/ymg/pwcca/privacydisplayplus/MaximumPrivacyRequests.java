package ymg.pwcca.privacydisplayplus;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Request provenance from one completed native layout; guarded by Samsung's privacy lock. */
final class MaximumPrivacyRequests {
    private final PolicyAccess access;
    private final List<Request> pending = new ArrayList<>();
    private List<Request> committed = List.of();

    MaximumPrivacyRequests(PolicyAccess access) { this.access = access; }

    void begin() { pending.clear(); }
    void finish() { committed = List.copyOf(pending); pending.clear(); }
    void clear() { pending.clear(); committed = List.of(); }

    Snapshot before(Object window) throws ReflectiveOperationException {
        Object task = access.task(window);
        if (task == null || access.userId(task) != 0 || access.displayId(window) != 0) return null;
        int mode = access.windowingMode(task);
        if (!supportedMode(task, mode)) return null;
        Object surface = access.bufferSurface(window);
        if (surface == null || !access.validSurface(surface)) return null;
        Object controller = access.controller(window);
        List<?> requests = access.requests(controller);
        return new Snapshot(window, task, surface, controller, mode, requests, new ArrayList<>(requests));
    }

    private boolean supportedMode(Object task, int mode) throws ReflectiveOperationException {
        if (mode == 1 || mode == 5) return true;
        return mode == 6 && access.splitScreen(task) && access.leafTask(task);
    }

    void after(Snapshot snapshot, boolean suppressed, boolean routedRegionally) throws ReflectiveOperationException {
        if (snapshot == null || suppressed) return;
        if (!unchanged(snapshot)) return;
        String source = appendedSource(snapshot);
        if (source == null) return;
        pending.add(new Request(source, new WeakReference<>(snapshot.task), !routedRegionally));
    }

    private boolean unchanged(Snapshot s) throws ReflectiveOperationException {
        return access.task(s.window) == s.task && access.userId(s.task) == 0
                && access.windowingMode(s.task) == s.mode && supportedMode(s.task, s.mode)
                && access.bufferSurface(s.window) == s.surface && access.validSurface(s.surface);
    }

    private String appendedSource(Snapshot s) throws ReflectiveOperationException {
        List<?> current = access.requests(s.controller);
        if (current != s.requests || current.size() != s.prefix.size() + 1) return null;
        if (!current.subList(0, s.prefix.size()).equals(s.prefix)) return null;
        Object source = current.get(s.prefix.size());
        return source instanceof String name && !name.isEmpty() ? name : null;
    }

    boolean wantsMaximum(ProtectionOptions options, boolean fullRequested, Set<?> appliedTasks) {
        for (Request request : committed) {
            if (!options.maximumFor(request.source)) continue;
            Object task = request.task.get();
            if (task == null) continue;
            if (appliedTasks.contains(task) || (request.full && fullRequested)) return true;
        }
        return false;
    }

    record Snapshot(Object window, Object task, Object surface, Object controller, int mode,
                    List<?> requests, List<?> prefix) {}
    private record Request(String source, WeakReference<Object> task, boolean full) {}
}
