package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Signature-protected control endpoint, registered only inside system_server. */
final class ModuleControl {
    private final RuntimeGate gate;
    private final ReflectivePolicyAccess access;
    private final WindowFeatures features;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private volatile Object service;

    ModuleControl(RuntimeGate gate, ReflectivePolicyAccess access, WindowFeatures features) {
        this.gate = gate;
        this.access = access;
        this.features = features;
    }

    void observeWindow(Object window) throws ReflectiveOperationException {
        if (service == null) service = access.windowService(window);
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> ams = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
        if (XposedBridge.hookAllMethods(ams, "systemReady", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!param.hasThrowable()) schedule();
            }
        }).isEmpty()) throw new NoSuchMethodException("ActivityManagerService.systemReady");
        if (bootComplete()) schedule();
    }

    private static boolean bootComplete() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return "1".equals(properties.getMethod("get", String.class).invoke(null, "sys.boot_completed"));
        } catch (ReflectiveOperationException ignored) { return false; }
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        new Handler(Looper.getMainLooper()).post(this::initialize);
    }

    private void initialize() {
        try {
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object current = thread.getMethod("currentActivityThread").invoke(null);
            Context context = (Context) thread.getMethod("getSystemContext").invoke(current);
            register(context);
            loadPreference(context);
            try { registerHandle(context); }
            catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException failure) {
                XposedBridge.log("Privacy Display+: popup control unavailable (" + failure.getClass().getSimpleName() + ")");
            }
        } catch (Throwable failure) {
            XposedBridge.log("Privacy Display+: control unavailable (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private void registerHandle(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        int systemUiUid = context.getPackageManager().getPackageUid(HandleCaller.PACKAGE, 0);
        IntentFilter filter = new IntentFilter(ControlProtocol.HANDLE_QUERY);
        filter.addAction(ControlProtocol.HANDLE_SET);
        filter.addAction(ControlProtocol.DRAG_QUERY);
        filter.addAction(ControlProtocol.DRAG_STATE);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!isOrderedBroadcast()) return;
                if (!HandleCaller.trusted(getSentFromUid(), getSentFromPackage(), systemUiUid)) {
                    setResult(Activity.RESULT_CANCELED, null, null);
                    return;
                }
                handlePopup(this, intent);
            }
        }, filter, "android.permission.MANAGE_ACTIVITY_TASKS", null, Context.RECEIVER_EXPORTED);
    }

    private void handlePopup(BroadcastReceiver receiver, Intent intent) {
        Bundle reply = new Bundle();
        int result = Activity.RESULT_CANCELED;
        try {
            requireHandleReady(intent);
            Object current = service;
            synchronized (access.globalLock(current)) {
                Object controller = access.controllerForService(current);
                synchronized (access.lock(controller)) {
                    boolean mutate = ControlProtocol.HANDLE_SET.equals(intent.getAction());
                    reply = popupReply(controller, intent, mutate);
                    if (mutate || ControlProtocol.DRAG_STATE.equals(intent.getAction())) access.requestTraversal(current);
                }
            }
            result = Activity.RESULT_OK;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            reply.putString(ControlProtocol.ERROR, "unavailable");
        }
        reply.putInt(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE);
        receiver.setResult(result, null, reply);
    }

    private Bundle popupReply(Object controller, Intent intent, boolean mutate) throws ReflectiveOperationException {
        if (ControlProtocol.DRAG_QUERY.equals(intent.getAction())) {
            Bundle result = features.drag(controller, intent.getExtras(), gate.options());
            scheduleDragExpiry();
            return result;
        }
        if (ControlProtocol.DRAG_STATE.equals(intent.getAction())) {
            Bundle result = features.dragState(controller, intent.getExtras());
            if (intent.getBooleanExtra(ControlProtocol.DRAG_ACTIVE, false)) scheduleDragExpiry();
            return result;
        }
        return features.handle(controller, intent.getExtras(), mutate);
    }

    private void scheduleDragExpiry() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { requestNativeTraversal(); }
            catch (ReflectiveOperationException | RuntimeException ignored) { /* A later native traversal retries. */ }
        }, 3100);
    }

    private void requireHandleReady(Intent intent) {
        if (intent.getIntExtra(ControlProtocol.VERSION, -1) != ControlProtocol.VERSION_CODE) {
            throw new IllegalArgumentException("Incompatible control version");
        }
        if (!canRoute() || service == null) throw new IllegalStateException("Not ready");
    }

    private void register(Context context) {
        IntentFilter filter = new IntentFilter(ControlProtocol.QUERY);
        filter.addAction(ControlProtocol.SET);
        filter.addAction(ControlProtocol.APPS);
        filter.addAction(ControlProtocol.WINDOWS);
        filter.addAction(ControlProtocol.TEMPORARY);
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) {
                if (!isOrderedBroadcast()) return;
                handle(this, intent);
            }
        }, filter, ControlProtocol.PERMISSION, null, Context.RECEIVER_EXPORTED);
    }

    private void loadPreference(Context context) {
        try {
            Bundle config = context.getContentResolver().call(
                    Uri.parse("content://" + ControlProtocol.AUTHORITY), ControlProtocol.GET_CONFIG, null, null);
            if (!ControlProtocol.hasOptions(config)) return;
            gate.apply(ControlProtocol.readOptions(config), this::refresh);
        } catch (RuntimeException | ReflectiveOperationException failure) {
            XposedBridge.log("Privacy Display+: saved preference unavailable; waiting for app control");
        }
    }

    private void handle(BroadcastReceiver receiver, Intent intent) {
        int result = Activity.RESULT_OK;
        Bundle status = new Bundle();
        try {
            if (intent.getIntExtra(ControlProtocol.VERSION, -1) != ControlProtocol.VERSION_CODE) {
                throw new IllegalArgumentException("Incompatible control version");
            }
            if (ControlProtocol.SET.equals(intent.getAction())) {
                gate.apply(ControlProtocol.readOptions(intent.getExtras()), this::refresh);
            }
            handleFeatures(intent, status);
        } catch (RuntimeException | ReflectiveOperationException failure) {
            result = Activity.RESULT_CANCELED;
            status.putString(ControlProtocol.ERROR, failure instanceof IllegalArgumentException ? "stale" : "unavailable");
        }
        status.putInt(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE);
        status.putBoolean(ControlProtocol.READY, gate.isReady());
        status.putBoolean(ControlProtocol.HEALTHY, gate.isHealthy());
        ControlProtocol.writeOptions(status, gate.options());
        status.putInt(ControlProtocol.TEMP_COUNT, features.count());
        status.putInt(ControlProtocol.OVERRIDE_COUNT, features.overrideCount());
        receiver.setResult(result, null, status);
    }

    private void handleFeatures(Intent intent, Bundle status) throws ReflectiveOperationException {
        String action = intent.getAction();
        if (!ControlProtocol.APPS.equals(action) && !ControlProtocol.WINDOWS.equals(action)
                && !ControlProtocol.TEMPORARY.equals(action)) return;
        if (!gate.isReady() || !gate.isHealthy() || service == null) throw new IllegalStateException("Not ready");
        Object current = service;
        synchronized (access.globalLock(current)) {
            Object controller = access.controllerForService(current);
            synchronized (access.lock(controller)) {
                featureReply(intent, status, current, controller);
            }
        }
    }

    private void featureReply(Intent intent, Bundle status, Object current, Object controller)
            throws ReflectiveOperationException {
        status.putBoolean(ControlProtocol.NATIVE_ALLOWED, features.allowed(controller));
        if (ControlProtocol.APPS.equals(intent.getAction())) {
            status.putStringArrayList(ControlProtocol.ITEMS, features.selectedApps(controller));
        } else if (ControlProtocol.WINDOWS.equals(intent.getAction())) {
            status.putParcelableArrayList(ControlProtocol.ITEMS, features.windows(current));
        } else {
            String token = intent.getStringExtra(ControlProtocol.TOKEN);
            if (token == null || token.length() > 64 || !intent.hasExtra(ControlProtocol.PROTECTED)) {
                throw new IllegalArgumentException("Missing temporary selection");
            }
            features.setTemporary(controller, token, intent.getBooleanExtra(ControlProtocol.PROTECTED, false));
            access.requestTraversal(current);
        }
    }

    boolean canRoute() { return gate.isReady() && gate.isHealthy(); }
    ProtectionOptions optionsFor(Object window) throws ReflectiveOperationException {
        return features.optionsFor(window, gate.options());
    }
    ProtectionOptions routingOptionsFor(Object window) throws ReflectiveOperationException {
        return features.suppresses(window) ? ProtectionOptions.BOTH : optionsFor(window);
    }
    boolean suppresses(Object window) throws ReflectiveOperationException { return features.suppresses(window); }
    void contributeTemporary(Object window) throws ReflectiveOperationException { features.contribute(window); }

    private void refresh() throws ReflectiveOperationException {
        Object current = service;
        if (current != null) access.requestTraversal(current);
    }

    void requestNativeTraversal() throws ReflectiveOperationException { refresh(); }
}
