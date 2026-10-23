package ymg.pwcca.privacydisplayplus;

import java.util.List;

/** One layout-scoped full-screen request; Samsung retains enable/disable arbitration. */
final class NotificationShadePrivacy {
    static final String SOURCE = "com.android.systemui";
    private final PolicyAccess access;
    private final RuntimeGate gate;

    NotificationShadePrivacy(PolicyAccess access, RuntimeGate gate) {
        this.access = access;
        this.gate = gate;
    }
    static boolean notificationCenterOpen(float fraction, boolean expanded, int panelState, int statusBarState,
            boolean dozing, boolean bouncerShowing) {
        return isEligiblePanel(fraction, expanded, panelState, statusBarState, dozing, bouncerShowing)
                && (panelState == 1 || panelState == 2 || panelState == 3);
    }

    static boolean quickSettingsOpen(float fraction, boolean expanded, int panelState, int statusBarState,
            boolean dozing, boolean bouncerShowing) {
        return isEligiblePanel(fraction, expanded, panelState, statusBarState, dozing, bouncerShowing)
                && (panelState == 0 || panelState == 2 || panelState == 3);
    }
    static boolean maximumRequested(boolean notificationCenterOpen, boolean quickSettingsOpen,
            ProtectionOptions options) {
        return maximumRequested(notificationCenterOpen, quickSettingsOpen, false, options);
    }

    static boolean maximumRequested(boolean notificationCenterOpen, boolean quickSettingsOpen,
            boolean lockScreenOpen, ProtectionOptions options) {
        return (notificationCenterOpen && options.notificationCenter && options.notificationCenterMaximum)
                || (quickSettingsOpen && options.quickSettings && options.quickSettingsMaximum)
                || (lockScreenOpen && options.lockScreen && options.lockScreenMaximum);
    }

    static boolean lockScreenOpen(boolean showing, boolean occluded, boolean goingAway, boolean fadingAway,
            boolean dozing, boolean bouncerShowing) {
        return showing && !occluded && !goingAway && !fadingAway && !dozing && !bouncerShowing;
    }

    private static boolean isEligiblePanel(float fraction, boolean expanded, int panelState, int statusBarState,
            boolean dozing, boolean bouncerShowing) {
        // A heads-up window can be expanded while its actual shade fraction is zero.
        return fraction > 0 && expanded && (statusBarState == 0 || statusBarState == 2)
                && !dozing && !bouncerShowing && panelState >= 0 && panelState <= 3;
    }

    @SuppressWarnings("unchecked")
    void contribute(Object controller, boolean notificationCenterOpen, boolean quickSettingsOpen,
            int displayId, int userId) throws ReflectiveOperationException {
        contribute(controller, notificationCenterOpen, quickSettingsOpen, false, displayId, userId);
    }

    void contribute(Object controller, boolean notificationCenterOpen, boolean quickSettingsOpen,
            boolean lockScreenOpen,
            int displayId, int userId) throws ReflectiveOperationException {
        if (displayId != 0 || userId != 0 || !gate.isReady() || !gate.isHealthy()) return;
        ProtectionOptions options = gate.options();
        if (!(notificationCenterOpen && options.notificationCenter)
                && !(quickSettingsOpen && options.quickSettings)
                && !(lockScreenOpen && options.lockScreen)) return;
        synchronized (access.lock(controller)) {
            if (access.mode(controller) != 2) return;
            int previousPolicy = access.appPolicy(controller);
            if ((previousPolicy & ~3) != 0) return;
            String previousPackage = access.packageName(controller);
            List<String> requests = (List<String>) access.requests(controller);
            boolean added = !requests.contains(SOURCE);
            try {
                if (added) requests.add(SOURCE);
                if (previousPolicy == 0) access.packageName(controller, SOURCE);
                access.appPolicy(controller, previousPolicy | 1);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                try { access.appPolicy(controller, previousPolicy); }
                catch (ReflectiveOperationException | RuntimeException rollback) { failure.addSuppressed(rollback); }
                try { access.packageName(controller, previousPackage); }
                catch (ReflectiveOperationException | RuntimeException rollback) { failure.addSuppressed(rollback); }
                if (added) requests.remove(SOURCE);
                throw failure;
            }
        }
    }

}
