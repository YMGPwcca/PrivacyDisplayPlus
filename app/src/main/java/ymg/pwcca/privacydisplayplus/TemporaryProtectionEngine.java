package ymg.pwcca.privacydisplayplus;

/** Adds only a task contribution; Samsung still owns suppression and full-display reasons. */
final class TemporaryProtectionEngine {
    interface Conditions {
        boolean selected(Object task) throws ReflectiveOperationException;
        boolean drawable(Object window) throws ReflectiveOperationException;
        boolean allowed(Object controller) throws ReflectiveOperationException;
        boolean intersects(Object window, Object controller) throws ReflectiveOperationException;
    }

    private final PolicyAccess access;
    private final SplitSurfacePrivacy split;
    private final Conditions conditions;
    TemporaryProtectionEngine(PolicyAccess access, SplitSurfacePrivacy split, Conditions conditions) {
        this.access = access;
        this.split = split;
        this.conditions = conditions;
    }

    void contribute(Object window) throws ReflectiveOperationException {
        Object task = access.task(window);
        if (task == null || !conditions.selected(task) || !conditions.drawable(window)) return;
        Object controller = access.controller(window);
        synchronized (access.lock(controller)) {
            if (!conditions.allowed(controller) || !conditions.intersects(window, controller)) return;
            if (access.windowingMode(task) == 6 && !enableSplit(window, task)) return;
            access.partialTasks(controller).add(task);
        }
    }

    private boolean enableSplit(Object window, Object task) throws ReflectiveOperationException {
        Object surface = access.bufferSurface(window);
        return surface != null && access.validSurface(surface) && split.enable(window, task, surface);
    }
}
