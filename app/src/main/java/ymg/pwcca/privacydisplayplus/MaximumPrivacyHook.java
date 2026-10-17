package ymg.pwcca.privacydisplayplus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Ephemeral level override. Never changes Samsung's persisted level or privacy enable reasons. */
final class MaximumPrivacyHook {
    private final ReflectivePolicyAccess access;
    private final RuntimeGate gate;
    private final MaximumPrivacyRequests requests;
    private final Field policyService, policyDisplay, displayId, currentUser, displayState;
    private final Field temporaryOff, fixedOff, requestedFull, appliedFull, appliedTasks, aiqe, showUser;
    private final Method begin, finish, applyPartial, writeLevel, notifyAiqe, setPd, applyState;
    private final Method[] callers;
    private final Field handlerController;
    private final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
    private boolean overriding;
    private boolean inLayout;
    private boolean reportedFailure;

    MaximumPrivacyHook(ClassLoader loader, ReflectivePolicyAccess access, RuntimeGate gate)
            throws ReflectiveOperationException {
        this.access = access;
        this.gate = gate;
        requests = new MaximumPrivacyRequests(access);
        Class<?> policy = Class.forName("com.android.server.wm.DisplayPolicy", false, loader);
        Class<?> controller = Class.forName("com.android.server.wm.PrivacyDisplayController", false, loader);
        Class<?> aiqeClass = Class.forName("com.samsung.android.displayaiqe.DisplayAiqeManager", false, loader);
        policyService = field(policy, "mService", Object.class);
        policyDisplay = field(policy, "mDisplayContent", Object.class);
        displayId = field(policyDisplay.getType(), "mDisplayId", int.class);
        currentUser = field(controller, "mCurrentUserId", int.class);
        displayState = field(controller, "mDisplayState", int.class);
        temporaryOff = field(controller, "mPrivacyDisplayTemporaryOff", int.class);
        fixedOff = field(controller, "mPrivacyDisplayFixedOff", int.class);
        requestedFull = field(controller, "mRequestedPrivacyDisplay", boolean.class);
        appliedFull = field(controller, "mPrivacyDisplayApplied", boolean.class);
        appliedTasks = field(controller, "mPartialPrivacyAppliedTasks", Set.class);
        aiqe = field(controller, "mAiqeManager", aiqeClass);
        showUser = field(Class.forName("com.android.server.wm.WindowState", false, loader), "mShowUserId", int.class);
        begin = method(policy, "beginPostLayoutPolicyLw");
        finish = method(policy, "finishPostLayoutPolicyLw");
        applyPartial = method(controller, "applyPartialPrivacyDisplayTasksLocked");
        writeLevel = method(controller, "sysfsWriteLevelLocked", long.class);
        notifyAiqe = method(controller, "notifyPDEnabledToAiqe", boolean.class);
        setPd = method(aiqeClass, "setPdEnabled", int.class, boolean.class, int.class);
        applyState = method(controller, "-$$Nest$mrequestApplyingPDState", controller, boolean.class, int.class, boolean.class);
        callers = new Method[]{
                method(Class.forName("com.android.server.wm.RootWindowContainer", false, loader), "applySurfaceChangesTransaction$1"),
                method(controller, "updateFullPrivacyDisplayStatusLocked"),
                method(Class.forName("com.android.server.wm.PrivacyDisplayController$BackgroundHandler", false, loader),
                        "handleMessage", android.os.Message.class),
                method(Class.forName("com.android.server.wm.PrivacyDisplayController$$ExternalSyntheticLambda2", false, loader), "run")
        };
        handlerController = field(callers[2].getDeclaringClass(), "this$0", controller);
    }

    private static Field field(Class<?> type, String name, Class<?> value) throws NoSuchFieldException {
        return ReflectivePolicyAccess.field(type, name, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        return ReflectivePolicyAccess.method(type, name, args);
    }

    void install(Method deoptimize) throws ReflectiveOperationException {
        try {
            // Preserve calls to these small methods even when ART has inlined them into callers.
            for (Method target : new Method[]{begin, finish, applyPartial, applyState, notifyAiqe}) {
                deoptimize.invoke(null, target);
            }
            for (Method caller : callers) deoptimize.invoke(null, caller);
            hook(begin, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    layout(param, false);
                }
            });
            hook(finish, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    publish(param);
                }
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    layout(param, true);
                }
            });
            hook(callers[2], new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || ((android.os.Message) param.args[0]).what != 2) return;
                    try {
                        Object controller = handlerController.get(param.thisObject);
                        synchronizedLock(controller, () -> notifyAiqe.invoke(controller, appliedFull.getBoolean(controller)));
                    } catch (ReflectiveOperationException failure) { fail(null, failure); }
                }
            });
            hook(applyPartial, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) refreshSafely(param.thisObject);
                }
            });
            hook(applyState, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object controller = param.args[0];
                    synchronizedLock(controller, () -> {
                        if (wantsMaximum(controller)) param.args[2] = 1;
                    });
                }
            });
            hook(notifyAiqe, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Object controller = param.thisObject;
                    synchronizedLock(controller, () -> {
                        if (!wantsMaximum(controller)) return;
                        Object manager = aiqe.get(controller);
                        if (manager == null) throw new IllegalStateException("Display AIQE unavailable");
                        if (!Boolean.TRUE.equals(setPd.invoke(manager, 0, true, 1))) {
                            throw new IllegalStateException("Display AIQE rejected Maximum");
                        }
                        param.setResult(null);
                    });
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            uninstall();
            throw failure;
        }
    }

    private void hook(Method method, XC_MethodHook callback) {
        XC_MethodHook.Unhook installed = XposedBridge.hookMethod(method, callback);
        if (installed == null) throw new IllegalStateException("Maximum privacy hook rejected");
        hooks.add(installed);
    }

    void uninstall() { for (XC_MethodHook.Unhook hook : hooks) hook.unhook(); hooks.clear(); }

    private void layout(XC_MethodHook.MethodHookParam param, boolean finished) {
        Object controller = null;
        try {
            if (displayId.getInt(policyDisplay.get(param.thisObject)) != 0) return;
            controller = access.controllerForService(policyService.get(param.thisObject));
            synchronized (access.lock(controller)) {
                inLayout = !finished && !param.hasThrowable();
                if (param.hasThrowable()) requests.clear();
                else if (!finished) requests.begin();
                if (finished || param.hasThrowable()) refresh(controller);
            }
        } catch (Throwable failure) { fail(controller, failure); }
    }

    private void publish(XC_MethodHook.MethodHookParam param) {
        Object controller = null;
        try {
            if (displayId.getInt(policyDisplay.get(param.thisObject)) != 0) return;
            controller = access.controllerForService(policyService.get(param.thisObject));
            synchronized (access.lock(controller)) {
                // All windows have been visited. Native finish may enqueue an enable immediately.
                requests.finish();
            }
        } catch (Throwable failure) { fail(controller, failure); }
    }

    MaximumPrivacyRequests.Snapshot before(Object window) throws ReflectiveOperationException {
        if (!gate.isReady() || !gate.isHealthy() || !gate.options().hasMaximumRules()) return null;
        if (showUser.getInt(window) != 0) return null;
        Object controller = access.controller(window);
        synchronized (access.lock(controller)) {
            if (access.mode(controller) != 2) return null;
            return requests.before(window);
        }
    }

    void after(MaximumPrivacyRequests.Snapshot snapshot, boolean suppressed, boolean routedRegionally)
            throws ReflectiveOperationException {
        if (snapshot == null) return;
        synchronized (access.lock(snapshot.controller())) {
            requests.after(snapshot, suppressed || showUser.getInt(snapshot.window()) != 0, routedRegionally);
        }
    }

    private boolean wantsMaximum(Object controller) throws ReflectiveOperationException {
        if (!gate.isReady() || !gate.isHealthy()) return false;
        MaximumPrivacyState state = new MaximumPrivacyState(currentUser.getInt(controller), access.mode(controller),
                displayState.getInt(controller), temporaryOff.getInt(controller), fixedOff.getInt(controller));
        if (!state.allowsOverride()) return false;
        return requests.wantsMaximum(gate.options(), requestedFull.getBoolean(controller), (Set<?>) appliedTasks.get(controller));
    }

    private void refresh(Object controller) throws ReflectiveOperationException {
        boolean desired = wantsMaximum(controller);
        boolean write = MaximumPrivacyState.needsLevelWrite(overriding, desired,
                requestedFull.getBoolean(controller), appliedFull.getBoolean(controller));
        overriding = desired;
        if (write) writeLevel.invoke(controller, 0L);
    }

    private void refreshSafely(Object controller) {
        synchronizedLock(controller, () -> { if (!inLayout) refresh(controller); });
    }

    private interface Operation { void run() throws ReflectiveOperationException; }
    private void synchronizedLock(Object controller, Operation operation) {
        try {
            synchronized (access.lock(controller)) { operation.run(); }
        } catch (Throwable failure) { fail(controller, failure); }
    }

    private void fail(Object controller, Throwable failure) {
        gate.disableOnFailure();
        if (controller != null) restore(controller);
        if (!reportedFailure) {
            reportedFailure = true;
            XposedBridge.log("Privacy Display+: Maximum privacy disabled (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private void restore(Object controller) {
        try {
            synchronized (access.lock(controller)) {
                requests.clear();
                inLayout = false;
                overriding = false;
                writeLevel.invoke(controller, 0L);
            }
        } catch (Throwable ignored) { /* Native callbacks retain the saved level; no settings were overwritten. */ }
    }
}
