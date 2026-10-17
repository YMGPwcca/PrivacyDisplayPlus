package ymg.pwcca.privacydisplayplus;

import java.util.List;

/** Recomputes the edge clamp from the live surface position, without waiting for WMS layout. */
final class FloatingDragSession {
    interface Writer {
        boolean valid(Object surface) throws ReflectiveOperationException;
        void queue(Object transaction, Object surface, SplitPrivacyRegion region) throws ReflectiveOperationException;
    }
    record Target(Object surface, SplitPrivacyRegion.Bounds window, SplitPrivacyRegion.Bounds display) {}
    private final Object taskSurface;
    private final SplitPrivacyRegion.Bounds task;
    private final List<Target> targets;
    private boolean closed;

    FloatingDragSession(Object taskSurface, SplitPrivacyRegion.Bounds task, List<Target> targets) {
        this.taskSurface = taskSurface;
        this.task = task;
        this.targets = List.copyOf(targets);
    }

    boolean move(Object transaction, Object movingSurface, float x, float y, float scale, Writer writer)
            throws ReflectiveOperationException {
        if (!active(movingSurface, x, y, scale)) return false;
        boolean queued = false;
        for (Target target : targets) {
            if (!writer.valid(target.surface)) continue;
            writer.queue(transaction, target.surface, region(target, x, y, scale));
            queued = true;
        }
        return queued;
    }

    private boolean active(Object surface, float x, float y, float scale) {
        return !closed && surface == taskSurface && validPosition(x, y, scale);
    }

    private static boolean validPosition(float x, float y, float scale) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(scale) && scale > 0 && scale <= 1;
    }

    private SplitPrivacyRegion region(Target target, float x, float y, float scale) {
        SplitPrivacyRegion.Bounds frame = target.window;
        int left = (int) Math.ceil(x + (frame.left() - task.left()) * scale);
        int top = (int) Math.ceil(y + (frame.top() - task.top()) * scale);
        int right = (int) Math.floor(x + (frame.right() - task.left()) * scale);
        int bottom = (int) Math.floor(y + (frame.bottom() - task.top()) * scale);
        return SplitPrivacyRegion.create(new SplitPrivacyRegion.Bounds(left, top, right, bottom), target.display);
    }

    void close() { closed = true; }
}
