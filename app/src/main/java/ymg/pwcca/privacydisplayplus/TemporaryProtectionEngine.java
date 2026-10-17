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
    private final WindowSurfacePrivacy surfaces;
    private final Conditions conditions;
    TemporaryProtectionEngine(PolicyAccess access, WindowSurfacePrivacy surfaces, Conditions conditions) {
        this.access = access;
        this.surfaces = surfaces;
        this.conditions = conditions;
    }

    void contribute(Object window) throws ReflectiveOperationException {
        Object task = access.task(window);
        if (!eligible(window, task)) return;
        Object controller = access.controller(window);
        synchronized (access.lock(controller)) {
            if (!conditions.allowed(controller) || !conditions.intersects(window, controller)) return;
            if (!enableRegion(window, task)) return;
            access.partialTasks(controller).add(task);
        }
    }

    private boolean eligible(Object window, Object task) throws ReflectiveOperationException {
        return task != null && conditions.selected(task) && conditions.drawable(window);
    }

    private boolean enableRegion(Object window, Object task) throws ReflectiveOperationException {
        Object surface = access.bufferSurface(window);
        return surface != null && access.validSurface(surface) && surfaces.enable(window, task, surface);
    }
}
