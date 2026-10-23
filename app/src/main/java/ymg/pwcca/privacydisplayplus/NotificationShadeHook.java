package ymg.pwcca.privacydisplayplus;

import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Publishes the shade request after window routing, before Samsung commits the layout. */
final class NotificationShadeHook {
    private final ReflectivePolicyAccess access;
    private final RuntimeGate gate;
    private final NotificationShadePrivacy privacy;
    private final Field policyService, policyDisplay, displayId, currentUser, shadeWindow;
    private final Method finish, visible;
    private XC_MethodHook.Unhook hook;
    private IBinder owner;
    private IBinder.DeathRecipient death;
    private boolean notificationCenterOpen, quickSettingsOpen, lockScreenOpen, reportedFailure;

    NotificationShadeHook(ClassLoader loader, ReflectivePolicyAccess access, RuntimeGate gate)
            throws ReflectiveOperationException {
        this.access = access;
        this.gate = gate;
        privacy = new NotificationShadePrivacy(access, gate);
        Class<?> policy = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
        Class<?> controller = Class.forName("com.android.server.wm.PrivacyDisplayController", false, loader);
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        policyService = ReflectivePolicyAccess.field(policy, "mService", Object.class);
        policyDisplay = ReflectivePolicyAccess.field(policy, "mDisplayContent", Object.class);
        displayId = ReflectivePolicyAccess.field(policyDisplay.getType(), "mDisplayId", int.class);
        currentUser = ReflectivePolicyAccess.field(controller, "mCurrentUserId", int.class);
        shadeWindow = ReflectivePolicyAccess.field(policy, "mNotificationShade", window);
        visible = ReflectivePolicyAccess.method(window, "isVisible");
        finish = ReflectivePolicyAccess.method(policy, "finishPostLayoutPolicyLw");
    }

    void install(Method deoptimize) throws ReflectiveOperationException {
        deoptimize.invoke(null, finish);
        hook = XposedBridge.hookMethod(finish, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                Object service = null;
                try {
                    if (!gate.isReady() || !gate.isHealthy()
                            || !(gate.options().notificationCenter || gate.options().quickSettings
                            || gate.options().lockScreen)
                            || !(notificationCenterIsOpen() || quickSettingsIsOpen() || lockScreenIsOpen())) return;
                    Object policy = param.thisObject;
                    int display = displayId.getInt(policyDisplay.get(policy));
                    if (display != 0) return;
                    service = policyService.get(policy);
                    Object window = shadeWindow.get(policy);
                    boolean centerOpen = window != null && (boolean) visible.invoke(window)
                            && notificationCenterIsOpen();
                    boolean qsOpen = window != null && (boolean) visible.invoke(window)
                            && quickSettingsIsOpen();
                    Object controller = access.controllerForService(service);
                    synchronized (access.lock(controller)) {
                        privacy.contribute(controller, centerOpen, qsOpen, lockScreenIsOpen(),
                                display, currentUser.getInt(controller));
                    }
                } catch (Throwable failure) { fail(service, failure); }
            }
        });
        if (hook == null) throw new IllegalStateException("Notification shade hook rejected");
    }

    synchronized boolean connected() { return owner != null; }
    private synchronized boolean notificationCenterIsOpen() { return notificationCenterOpen; }
    private synchronized boolean quickSettingsIsOpen() { return quickSettingsOpen; }
    private synchronized boolean lockScreenIsOpen() { return lockScreenOpen; }
    synchronized boolean panelOpen() { return notificationCenterOpen || quickSettingsOpen; }
    boolean maximumRequested() {
        ProtectionOptions options = gate.options();
        return gate.isReady() && gate.isHealthy() && NotificationShadePrivacy.maximumRequested(
                notificationCenterIsOpen(), quickSettingsIsOpen(), lockScreenIsOpen(), options);
    }

    void update(Object service, Bundle state) {
        if (state == null || !state.containsKey(ControlProtocol.NOTIFICATION_CENTER_OPEN)
                || !state.containsKey(ControlProtocol.QUICK_SETTINGS_OPEN)
                || !state.containsKey(ControlProtocol.LOCK_SCREEN_OPEN)) {
            throw new IllegalArgumentException("Missing panel state");
        }
        IBinder source = state.getBinder(ControlProtocol.SHADE_OWNER);
        if (source == null) throw new IllegalArgumentException("Missing shade source");
        synchronized (this) {
            if (!source.equals(owner)) {
                IBinder.DeathRecipient recipient = () -> died(service, source);
                try { source.linkToDeath(recipient, 0); }
                catch (RemoteException failure) { throw new IllegalStateException("Shade source exited", failure); }
                if (owner != null) owner.unlinkToDeath(death, 0);
                owner = source;
                death = recipient;
            }
            notificationCenterOpen = state.getBoolean(ControlProtocol.NOTIFICATION_CENTER_OPEN);
            quickSettingsOpen = state.getBoolean(ControlProtocol.QUICK_SETTINGS_OPEN);
            lockScreenOpen = state.getBoolean(ControlProtocol.LOCK_SCREEN_OPEN);
        }
    }

    private void died(Object service, IBinder source) {
        synchronized (this) {
            if (!source.equals(owner)) return;
            owner = null;
            notificationCenterOpen = false;
            quickSettingsOpen = false;
            lockScreenOpen = false;
        }
        try { access.requestTraversal(service); }
        catch (ReflectiveOperationException | RuntimeException failure) { fail(service, failure); }
    }

    void uninstall() {
        if (hook != null) hook.unhook();
        synchronized (this) {
            if (owner != null) owner.unlinkToDeath(death, 0);
            owner = null;
            notificationCenterOpen = false;
            quickSettingsOpen = false;
            lockScreenOpen = false;
        }
    }

    private void fail(Object service, Throwable failure) {
        gate.disableOnFailure();
        if (service != null) {
            try { access.requestTraversal(service); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* Native layout rebuilds requests. */ }
        }
        synchronized (this) {
            if (reportedFailure) return;
            reportedFailure = true;
        }
        XposedBridge.log("Privacy Display+: notification shade disabled (" + failure.getClass().getSimpleName() + ")");
    }
}
