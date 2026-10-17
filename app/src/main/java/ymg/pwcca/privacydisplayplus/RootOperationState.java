package ymg.pwcca.privacydisplayplus;

import java.util.concurrent.CopyOnWriteArraySet;

/** Process-wide state survives Activity rotation without repeating a privileged action. */
final class RootOperationState {
    private final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
    private boolean running, pending;
    private RootCommandRunner.Result result;

    synchronized boolean begin() {
        if (running || pending) return false;
        running = true;
        result = null;
        changed();
        return true;
    }

    synchronized void finish(RootCommandRunner.Result outcome) {
        running = false;
        pending = outcome == RootCommandRunner.Result.REQUESTED
                || outcome == RootCommandRunner.Result.TIMED_OUT;
        result = outcome;
        changed();
    }

    synchronized boolean isRunning() { return running; }
    synchronized boolean isPending() { return pending; }
    synchronized RootCommandRunner.Result result() { return result; }
    void add(Runnable listener) { listeners.add(listener); listener.run(); }
    void remove(Runnable listener) { listeners.remove(listener); }
    private void changed() { for (Runnable listener : listeners) listener.run(); }
}
