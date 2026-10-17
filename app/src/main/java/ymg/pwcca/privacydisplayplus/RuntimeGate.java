package ymg.pwcca.privacydisplayplus;

/** Configuration and hook health are separate: a toggle cannot revive a failed hook. */
final class RuntimeGate {
    private volatile ProtectionOptions options = ProtectionOptions.NONE;
    private volatile boolean ready;
    private volatile boolean healthy = true;

    interface Refresh { void run() throws ReflectiveOperationException; }

    ProtectionOptions options() { return options; }
    boolean isReady() { return ready; }
    boolean isHealthy() { return healthy; }
    boolean shouldRoute() { return ready && healthy && options.anyEnabled(); }
    void disableOnFailure() { healthy = false; }

    synchronized void apply(ProtectionOptions desired, Refresh refresh) throws ReflectiveOperationException {
        if (!healthy) throw new IllegalStateException("Routing hook needs reload");
        ProtectionOptions previous = options;
        boolean previouslyReady = ready;
        options = desired;
        ready = true;
        try {
            refresh.run();
        } catch (RuntimeException | ReflectiveOperationException failure) {
            options = previous;
            ready = previouslyReady;
            throw failure;
        }
    }
}
