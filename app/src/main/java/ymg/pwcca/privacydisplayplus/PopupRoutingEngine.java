package ymg.pwcca.privacydisplayplus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Converts only an app-policy contribution that Samsung actually made for this window. */
final class PopupRoutingEngine {
    private static final int CUSTOM_MODE = 2;
    private static final int APP_REASON = 1;
    private static final int KNOWN_REASONS = 3;
    private static final int SECURED_SCREEN_OPTION = 256;
    private static final int SAMSUNG_SECURED_WINDOW = 4;
    private static final int FREEFORM_MODE = 5;
    private static final int MULTI_WINDOW_MODE = 6;
    private final PolicyAccess access;
    private final WindowSurfacePrivacy windowSurfaces;

    PopupRoutingEngine(PolicyAccess access, WindowSurfacePrivacy windowSurfaces) {
        this.access = access;
        this.windowSurfaces = windowSurfaces;
    }

    Snapshot before(Object window, ProtectionOptions options) throws ReflectiveOperationException {
        Object task = access.task(window);
        if (task == null) return null;
        int windowingMode = regionalMode(task);
        if (!options.mayRoute(windowingMode)) return null;
        if (access.displayId(window) != 0) return null;
        Object controller = access.controller(window);
        synchronized (access.lock(controller)) {
            return snapshot(window, task, controller, windowingMode);
        }
    }

    private Snapshot snapshot(Object window, Object task, Object controller, int windowingMode)
            throws ReflectiveOperationException {
        if (!eligible(controller, window)) return null;
        Object surface = liveSurface(access.surface(task));
        if (surface == null) return null;
        Object buffer = liveSurface(access.bufferSurface(window));
        if (buffer == null) return null;
        int policy = access.appPolicy(controller);
        if ((policy & ~KNOWN_REASONS) != 0) return null;
        List<?> requests = access.requests(controller);
        return new Snapshot(window, task, surface, buffer, controller, windowingMode, policy,
                access.option(controller), access.packageName(controller), requests,
                new ArrayList<>(requests));
    }

    private int regionalMode(Object task) throws ReflectiveOperationException {
        int mode = access.windowingMode(task);
        if (mode == FREEFORM_MODE) return mode;
        if (mode != MULTI_WINDOW_MODE) return 0;
        // Samsung marks actual split stages separately from generic multi-window mode.
        // Selection and lifecycle belong to the app's leaf task.
        if (!access.splitScreen(task) || !access.leafTask(task)) return 0;
        return mode;
    }

    private Object liveSurface(Object surface) throws ReflectiveOperationException {
        return surface != null && access.validSurface(surface) ? surface : null;
    }

    boolean after(Snapshot snapshot, ProtectionOptions options) throws ReflectiveOperationException {
        if (snapshot == null) return false;
        synchronized (access.lock(snapshot.controller)) {
            if (!unchangedWindow(snapshot)) return false;
            if (!observedAppRequest(snapshot)) return false;
            String selectedPackage = (String) access.requests(snapshot.controller).get(snapshot.prefix.size());
            if (!options.forApp(selectedPackage, access.userId(snapshot.task)).allows(snapshot.windowingMode)) return false;
            return convert(snapshot, selectedPackage);
        }
    }

    boolean suppressAppRequest(Snapshot snapshot) throws ReflectiveOperationException {
        if (snapshot == null) return false;
        synchronized (access.lock(snapshot.controller)) {
            if (!unchangedWindow(snapshot) || !observedAppRequest(snapshot)) return false;
            int originalPolicy = access.appPolicy(snapshot.controller);
            String originalPackage = access.packageName(snapshot.controller);
            try {
                access.packageName(snapshot.controller, snapshot.packageName);
                access.appPolicy(snapshot.controller, snapshot.policy);
                return true;
            } catch (RuntimeException | ReflectiveOperationException failure) {
                try { access.appPolicy(snapshot.controller, originalPolicy); }
                catch (RuntimeException | ReflectiveOperationException rollback) { failure.addSuppressed(rollback); }
                try { access.packageName(snapshot.controller, originalPackage); }
                catch (RuntimeException | ReflectiveOperationException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    private boolean eligible(Object controller, Object window) throws ReflectiveOperationException {
        if (access.mode(controller) != CUSTOM_MODE) return false;
        boolean securedOption = (access.option(controller) & SECURED_SCREEN_OPTION) != 0;
        boolean securedWindow = (access.samsungFlags(window) & SAMSUNG_SECURED_WINDOW) != 0;
        return !(securedOption && securedWindow);
    }

    private boolean unchangedWindow(Snapshot s) throws ReflectiveOperationException {
        if (!eligible(s.controller, s.window)) return false;
        if (access.option(s.controller) != s.option) return false;
        if (access.task(s.window) != s.task || regionalMode(s.task) != s.windowingMode) return false;
        if (liveSurface(access.surface(s.task)) != s.surface) return false;
        return liveSurface(access.bufferSurface(s.window)) == s.buffer;
    }

    private boolean observedAppRequest(Snapshot s) throws ReflectiveOperationException {
        if (access.appPolicy(s.controller) != (s.policy | APP_REASON)) return false;
        List<?> current = access.requests(s.controller);
        if (current != s.requests || current.size() != s.prefix.size() + 1) return false;
        if (!current.subList(0, s.prefix.size()).equals(s.prefix)) return false;
        Object appended = current.get(s.prefix.size());
        return appended instanceof String && !((String) appended).isEmpty();
    }

    private boolean convert(Snapshot s, String selectedPackage) throws ReflectiveOperationException {
        int originalPolicy = access.appPolicy(s.controller);
        String originalPackage = access.packageName(s.controller);
        Set<Object> partial = access.partialTasks(s.controller);
        boolean wasPresent = partial.contains(s.task);
        try {
            // Keep Samsung's package request list for manual-off bookkeeping.
            // Queue partial coverage BEFORE removing this window's full-screen contribution.
            if (!windowSurfaces.enable(s.window, s.task, s.buffer, selectedPackage)) return false;
            partial.add(s.task);
            access.packageName(s.controller, s.packageName);
            access.appPolicy(s.controller, s.policy);
            return true;
        } catch (RuntimeException | ReflectiveOperationException failure) {
            recover(s, partial, wasPresent, originalPolicy, originalPackage, failure);
            throw failure;
        }
    }

    private void recover(Snapshot s, Set<Object> partial, boolean wasPresent,
                         int originalPolicy, String originalPackage, Exception failure) {
        // If full-screen restoration fails, retain partial coverage conservatively.
        try {
            access.appPolicy(s.controller, originalPolicy);
            if (!wasPresent) partial.remove(s.task);
        } catch (RuntimeException | ReflectiveOperationException rollback) {
            failure.addSuppressed(rollback);
        }
        try {
            access.packageName(s.controller, originalPackage);
        } catch (RuntimeException | ReflectiveOperationException rollback) {
            failure.addSuppressed(rollback);
        }
    }

    static final class Snapshot {
        final Object window, task, surface, buffer, controller;
        final int windowingMode, policy, option;
        final String packageName;
        final List<?> requests, prefix;

        Snapshot(Object window, Object task, Object surface, Object buffer, Object controller, int windowingMode, int policy,
                 int option, String packageName, List<?> requests, List<?> prefix) {
            this.window = window;
            this.task = task;
            this.surface = surface;
            this.buffer = buffer;
            this.controller = controller;
            this.windowingMode = windowingMode;
            this.policy = policy;
            this.option = option;
            this.packageName = packageName;
            this.requests = requests;
            this.prefix = prefix;
        }
    }
}
