package ymg.pwcca.privacydisplayplus;

import android.os.Build;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy Xposed entry, with a signature-protected live control channel. */
public final class PrivacyDisplayModule implements IXposedHookLoadPackage {
    private static final String TAG = "Privacy Display+: ";
    private static final String SNAPSHOT = "s26.popup.privacy.snapshot";
    private static final String MAXIMUM_SNAPSHOT = "s26.maximum.privacy.snapshot";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loaded) {
        if (HandleCaller.PACKAGE.equals(loaded.packageName) && HandleCaller.PACKAGE.equals(loaded.processName)) {
            if (!FirmwareProfile.supports(Build.VERSION.SDK_INT, Build.FINGERPRINT)) return;
            if (!INSTALLED.compareAndSet(false, true)) return;
            try { SystemUiPopupHook.install(loaded.classLoader); }
            catch (Throwable failure) { log("popup handle hook not installed (" + failure.getClass().getSimpleName() + ")"); }
            try { SystemUiDragHook.install(loaded.classLoader); }
            catch (Throwable failure) { log("floating drag hook not installed (" + failure.getClass().getSimpleName() + ")"); }
            try { SystemUiShadeHook.install(loaded.classLoader); }
            catch (Throwable failure) { log("notification shade hook not installed (" + failure.getClass().getSimpleName() + ")"); }
            return;
        }
        if (!"android".equals(loaded.packageName) || !"android".equals(loaded.processName)) return;
        if (!FirmwareProfile.supports(Build.VERSION.SDK_INT, Build.FINGERPRINT)) {
            log("unsupported firmware; Samsung behavior retained");
            return;
        }
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            install(loaded.classLoader);
        } catch (Throwable failure) {
            log("hook not installed (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private static void install(ClassLoader loader) throws ReflectiveOperationException {
        ReflectivePolicyAccess access = new ReflectivePolicyAccess(loader);
        RuntimeGate gate = new RuntimeGate();
        NotificationShadeHook shade = new NotificationShadeHook(loader, access, gate);
        WindowSurfacePrivacy windowSurfaces = new WindowSurfacePrivacy(access,
                () -> System.nanoTime() / 1_000_000, shade::panelOpen);
        WindowFeatures features = new WindowFeatures(loader, access, windowSurfaces);
        ModuleControl control = new ModuleControl(gate, access, features, shade);
        MaximumPrivacyHook maximum = new MaximumPrivacyHook(loader, access, gate, shade);
        Class<?> visitor = Class.forName(FirmwareProfile.VISITOR, false, loader);
        Method target = ReflectivePolicyAccess.method(visitor, FirmwareProfile.VISIT_METHOD, Object.class);
        Method dispatcher = ReflectivePolicyAccess.method(visitor, "accept", Object.class);
        // The private visitor can be AOT-inlined into this synthetic dispatcher.
        // Require LSPosed's extension; do not silently claim success without deoptimization.
        Method deoptimize = XposedBridge.class.getMethod("deoptimizeMethod", Member.class);
        deoptimize.invoke(null, dispatcher);
        XC_MethodHook.Unhook hook = XposedBridge.hookMethod(target,
                new RoutingHook(new PopupRoutingEngine(access, windowSurfaces), windowSurfaces,
                        gate, control, maximum));
        if (hook == null) throw new IllegalStateException("Hook installation rejected");
        XC_MethodHook.Unhook surfaceHook = null;
        XC_MethodHook.Unhook removalHook = null;
        java.util.List<XC_MethodHook.Unhook> lifecycleHooks = new java.util.ArrayList<>();
        try {
            Class<?> container = Class.forName("com.android.server.wm.WindowContainer", false, loader);
            Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
            deoptimize.invoke(null, ReflectivePolicyAccess.method(container, "prepareSurfaces"));
            surfaceHook = XposedBridge.hookMethod(ReflectivePolicyAccess.method(window, "prepareSurfaces"),
                    new WindowCleanupHook(windowSurfaces, gate, control));
            if (surfaceHook == null) throw new IllegalStateException("Surface hook rejected");
            Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
            removalHook = XposedBridge.hookMethod(ReflectivePolicyAccess.method(task, "removeImmediately", String.class),
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) { features.removed(param.thisObject); }
                    });
            if (removalHook == null) throw new IllegalStateException("Task lifecycle hook rejected");
            XC_MethodHook changed = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try { features.changed(param.thisObject); }
                    catch (ReflectiveOperationException | RuntimeException failure) { features.removed(param.thisObject); }
                }
            };
            lifecycleHooks.add(XposedBridge.hookMethod(ReflectivePolicyAccess.method(task, "onConfigurationChangedInner",
                    android.content.res.Configuration.class), changed));
            Class<?> configuration = Class.forName("com.android.server.wm.ConfigurationContainer", false, loader);
            lifecycleHooks.add(XposedBridge.hookMethod(ReflectivePolicyAccess.method(task, "onParentChanged",
                    configuration, configuration), changed));
            if (lifecycleHooks.contains(null)) throw new IllegalStateException("Task lifecycle hook rejected");
            maximum.install(deoptimize);
            shade.install(deoptimize);
            control.install(loader);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            gate.disableOnFailure();
            maximum.uninstall();
            shade.uninstall();
            if (surfaceHook != null) surfaceHook.unhook();
            if (removalHook != null) removalHook.unhook();
            for (XC_MethodHook.Unhook installed : lifecycleHooks) if (installed != null) installed.unhook();
            hook.unhook();
            throw failure;
        }
        log("hook installed; awaiting an eligible popup or split-screen task");
    }

    private static void log(String message) { XposedBridge.log(TAG + message); }

    private static final class WindowCleanupHook extends XC_MethodHook {
        private final WindowSurfacePrivacy surfaces;
        private final RuntimeGate gate;
        private final ModuleControl control;
        private final AtomicBoolean reportedFailure = new AtomicBoolean();

        WindowCleanupHook(WindowSurfacePrivacy surfaces, RuntimeGate gate, ModuleControl control) {
            this.surfaces = surfaces;
            this.gate = gate;
            this.control = control;
        }

        @Override protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable()) return;
            try {
                surfaces.prepare(param.thisObject, control.canRoute() ? control.optionsFor(param.thisObject) : ProtectionOptions.NONE);
            } catch (Throwable failure) {
                gate.disableOnFailure();
                try { control.requestNativeTraversal(); }
                catch (ReflectiveOperationException | RuntimeException ignored) { /* Retry via native layout. */ }
                if (reportedFailure.compareAndSet(false, true)) {
                    log("window surface cleanup failed (" + failure.getClass().getSimpleName() + ")");
                }
            }
        }
    }

    private static final class RoutingHook extends XC_MethodHook {
        private final PopupRoutingEngine engine;
        private final WindowSurfacePrivacy surfaces;
        private final RuntimeGate gate;
        private final ModuleControl control;
        private final AtomicBoolean reportedConversion = new AtomicBoolean();
        private final MaximumPrivacyHook maximum;
        private volatile boolean enabled = true;

        RoutingHook(PopupRoutingEngine engine, WindowSurfacePrivacy surfaces, RuntimeGate gate,
                    ModuleControl control, MaximumPrivacyHook maximum) {
            this.engine = engine;
            this.surfaces = surfaces;
            this.gate = gate;
            this.control = control;
            this.maximum = maximum;
        }

        @Override protected void beforeHookedMethod(MethodHookParam param) {
            if (!enabled) return;
            try {
                control.observeWindow(param.args[0]);
                param.setObjectExtra(MAXIMUM_SNAPSHOT, maximum.before(param.args[0]));
                if (!control.canRoute()) return;
                param.setObjectExtra(SNAPSHOT, engine.before(param.args[0], control.routingOptionsFor(param.args[0])));
            } catch (Throwable failure) { disable(failure); }
        }

        @Override protected void afterHookedMethod(MethodHookParam param) {
            if (!enabled || !control.canRoute() || param.hasThrowable()) return;
            try {
                PopupRoutingEngine.Snapshot snapshot =
                        (PopupRoutingEngine.Snapshot) param.getObjectExtra(SNAPSHOT);
                boolean covered = snapshot != null && surfaces.suspended(param.args[0]);
                boolean suppressed = covered || control.suppresses(param.args[0]);
                boolean converted = suppressed ? engine.suppressAppRequest(snapshot)
                        : engine.after(snapshot, control.optionsFor(param.args[0]));
                if (converted && reportedConversion.compareAndSet(false, true)) {
                    log("app window privacy request handled; verify on the panel");
                }
                if (!converted && !covered) control.contributeTemporary(param.args[0]);
                maximum.after((MaximumPrivacyRequests.Snapshot) param.getObjectExtra(MAXIMUM_SNAPSHOT),
                        suppressed, converted);
            } catch (Throwable failure) { disable(failure); }
        }

        private void disable(Throwable failure) {
            enabled = false;
            gate.disableOnFailure();
            try { control.requestNativeTraversal(); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* Next native traversal restores policy. */ }
            log("routing disabled for this process (" + failure.getClass().getSimpleName() + ")");
        }
    }
}
