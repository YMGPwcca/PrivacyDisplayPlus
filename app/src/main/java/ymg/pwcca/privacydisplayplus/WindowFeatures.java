package ymg.pwcca.privacydisplayplus;

import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Read-only Samsung selection and task-scoped requests on the inspected firmware. */
final class WindowFeatures implements TemporaryProtectionEngine.Conditions {
    private final ReflectivePolicyAccess access;
    private final TemporaryProtectionEngine temporaryEngine;
    private final TemporaryTasks temporary = new TemporaryTasks();
    private final Field root, removing, taskId, ownerUser, appPolicyChange, valuesByUser, displayId;
    private final Field temporaryOff, fixedOff, targetRect, frames, frame, attrs;
    private final Field activity, transitionController, transitionFeature;
    private final Method allLeafTasks, allWindows, attached, displayContent, basePackage, isApp, taskBounds, displayBounds, ready;
    private final Method fullyTransparent, inTransition, transientHide, visibleRequested, taskVisible;
    private final Class<?> taskTokenClass;
    private final Method fromTaskToken;

    WindowFeatures(ClassLoader loader, ReflectivePolicyAccess access, SplitSurfacePrivacy split)
            throws ReflectiveOperationException {
        this.access = access;
        temporaryEngine = new TemporaryProtectionEngine(access, split, this);
        Class<?> service = Class.forName("com.android.server.wm.WindowManagerService", false, loader);
        Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
        Class<?> controller = Class.forName("com.android.server.wm.PrivacyDisplayController", false, loader);
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        Class<?> changes = Class.forName("com.samsung.android.server.packagefeature.PackageFeatureUserChange", false, loader);
        Class<?> activityClass = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        Class<?> transitions = Class.forName("com.android.server.wm.TransitionController", false, loader);
        root = field(service, "mRoot", Object.class);
        removing = field(task, "mRemoving", boolean.class);
        taskId = field(task, "mTaskId", int.class);
        ownerUser = field(task, "mUserId", int.class);
        allLeafTasks = method(root.getType(), "forAllLeafTasks", Consumer.class, boolean.class);
        allWindows = method(task, "forAllWindows", Consumer.class, boolean.class);
        attached = method(task, "isAttached");
        displayContent = method(task, "getDisplayContent");
        displayId = field(displayContent.getReturnType(), "mDisplayId", int.class);
        displayBounds = method(displayContent.getReturnType(), "getBounds");
        basePackage = method(task, "getBasePackageName");
        isApp = method(task, "isActivityTypeStandard");
        taskBounds = method(task, "getBounds");
        appPolicyChange = field(controller, "mAppPolicyChange", changes);
        valuesByUser = field(changes, "mChangeValuesAsUser", Map.class);
        temporaryOff = field(controller, "mPrivacyDisplayTemporaryOff", int.class);
        fixedOff = field(controller, "mPrivacyDisplayFixedOff", int.class);
        targetRect = field(controller, "mLayoutTargetDisplayRect", Rect.class);
        frames = field(window, "mWindowFrames", Object.class);
        frame = field(frames.getType(), "mFrame", Rect.class);
        attrs = field(window, "mAttrs", WindowManager.LayoutParams.class);
        ready = method(window, "isReadyForDisplay");
        fullyTransparent = method(window, "isFullyTransparent");
        activity = field(window, "mActivityRecord", activityClass);
        transitionController = field(window, "mTransitionController", transitions);
        inTransition = method(activityClass, "inTransition");
        transientHide = method(transitions, "isTransientHide", task);
        visibleRequested = method(activityClass, "isVisibleRequested");
        taskVisible = method(task, "isVisible");
        transitionFeature = field(Class.forName("com.samsung.android.rune.CoreRune", false, loader),
                "FW_PRIVACY_DISPLAY_TRANSITION", boolean.class);
        taskTokenClass = Class.forName("android.window.WindowContainerToken", false, loader);
        fromTaskToken = method(task, "fromWindowContainerToken", taskTokenClass);
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        return ReflectivePolicyAccess.field(owner, name, type);
    }
    private static Method method(Class<?> owner, String name, Class<?>... args) throws NoSuchMethodException {
        return ReflectivePolicyAccess.method(owner, name, args);
    }

    @Override public boolean allowed(Object controller) throws ReflectiveOperationException {
        // Same gates as Samsung applyPartialPrivacyDisplayTasksLocked on this firmware.
        return access.mode(controller) == 2 && (temporaryOff.getInt(controller) & 16) == 0
                && (fixedOff.getInt(controller) & 262144) == 0;
    }

    ArrayList<String> selectedApps(Object controller) throws IllegalAccessException {
        Map<?, ?> byUser = (Map<?, ?>) valuesByUser.get(appPolicyChange.get(controller));
        Object owner = byUser.get(0);
        ArrayList<String> result = new ArrayList<>();
        if (!(owner instanceof Map<?, ?> values)) return result;
        for (var entry : values.entrySet()) {
            if (Integer.valueOf(1).equals(entry.getValue()) && entry.getKey() instanceof String name) result.add(name);
        }
        result.sort(Comparator.naturalOrder());
        return new ArrayList<>(result.subList(0, Math.min(result.size(), 256)));
    }

    int regionalMode(Object task) throws ReflectiveOperationException {
        int mode = access.windowingMode(task);
        if (mode == 5) return 5;
        return mode == 6 && access.splitScreen(task) && access.leafTask(task) ? 6 : 0;
    }

    private boolean openTask(Object task) throws ReflectiveOperationException {
        if (!(boolean) attached.invoke(task) || removing.getBoolean(task)) return false;
        Object display = displayContent.invoke(task);
        if (ownerUser.getInt(task) != 0 || display == null || displayId.getInt(display) != 0) return false;
        return (boolean) isApp.invoke(task) && access.leafTask(task) && regionalMode(task) != 0;
    }

    ArrayList<Bundle> windows(Object service) throws ReflectiveOperationException {
        ArrayList<Object> tasks = new ArrayList<>();
        allLeafTasks.invoke(root.get(service), (Consumer<Object>) tasks::add, true);
        ArrayList<Bundle> rows = new ArrayList<>();
        for (Object task : tasks) {
            if (rows.size() == 32) break;
            if (!openTask(task)) { temporary.remove(task); continue; }
            if (!hasClientWindow(task)) continue;
            String name = (String) basePackage.invoke(task);
            if (name == null || ControlProtocol.PACKAGE.equals(name)) continue;
            int mode = regionalMode(task);
            TemporaryTasks.Entry entry = temporary.offer(task, mode);
            Bundle row = new Bundle();
            row.putString(ControlProtocol.TOKEN, entry.token());
            row.putString("package", name);
            row.putInt("task_id", taskId.getInt(task));
            row.putInt("mode", mode);
            row.putBoolean(ControlProtocol.PROTECTED, entry.protectedNow());
            row.putInt(ControlProtocol.OVERRIDE, entry.override());
            row.putBoolean("visible", (boolean) taskVisible.invoke(task));
            Rect bounds = (Rect) taskBounds.invoke(task);
            row.putIntArray("bounds", new int[]{bounds.left, bounds.top, bounds.right, bounds.bottom});
            Object display = displayContent.invoke(task);
            Rect panel = (Rect) displayBounds.invoke(display);
            row.putIntArray("display_bounds", new int[]{panel.left, panel.top, panel.right, panel.bottom});
            rows.add(row);
        }
        return rows;
    }

    private boolean hasClientWindow(Object task) throws ReflectiveOperationException {
        List<Object> windows = new ArrayList<>();
        allWindows.invoke(task, (Consumer<Object>) windows::add, true);
        for (Object window : windows) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
            if (params.type < 1 || params.type >= 1000) continue;
            Object surface = access.bufferSurface(window);
            if (surface != null && access.validSurface(surface)) return true;
        }
        return false;
    }

    void setTemporary(Object controller, String token, boolean enabled) throws ReflectiveOperationException {
        Object task = temporary.resolve(token);
        if (task == null || !openTask(task)) throw new IllegalArgumentException("Window closed or changed");
        if (!temporary.offer(task, regionalMode(task)).token().equals(token)) {
            throw new IllegalArgumentException("Window mode changed");
        }
        if (enabled && !allowed(controller)) throw new IllegalStateException("Samsung conditions are unavailable");
        if (enabled && !hasClientWindow(task)) throw new IllegalArgumentException("Window closed");
        temporary.set(task, regionalMode(task), enabled);
    }

    boolean protects(Object task) throws ReflectiveOperationException {
        return decision(task) == TemporaryTasks.ON;
    }

    private int decision(Object task) throws ReflectiveOperationException {
        if (task == null || !temporary.known(task)) return TemporaryTasks.FOLLOW;
        if (!openTask(task)) { temporary.remove(task); return TemporaryTasks.FOLLOW; }
        return temporary.decision(task, regionalMode(task));
    }

    ProtectionOptions optionsFor(Object window, ProtectionOptions options) throws ReflectiveOperationException {
        return switch (decision(access.task(window))) {
            case TemporaryTasks.ON -> ProtectionOptions.BOTH;
            case TemporaryTasks.OFF -> ProtectionOptions.NONE;
            default -> options;
        };
    }

    boolean suppresses(Object window) throws ReflectiveOperationException {
        return decision(access.task(window)) == TemporaryTasks.OFF;
    }

    Bundle handle(Object controller, Bundle command, boolean mutate) throws ReflectiveOperationException {
        if (command == null) throw new IllegalArgumentException("Missing window");
        Parcelable token = command.getParcelable(ControlProtocol.TASK_TOKEN, Parcelable.class);
        if (!taskTokenClass.isInstance(token)) throw new IllegalArgumentException("Invalid window token");
        Object task = fromTaskToken.invoke(null, token);
        if (task == null || !openTask(task) || regionalMode(task) != 5 || !hasClientWindow(task)) {
            throw new IllegalArgumentException("Popup closed or changed");
        }
        if (mutate) applyHandleOverride(controller, task, command);
        Bundle reply = new Bundle();
        int value = decision(task);
        boolean requested = value == TemporaryTasks.ON || (value == TemporaryTasks.FOLLOW && selectedTask(controller, task));
        reply.putBoolean(ControlProtocol.PROTECTED, requested);
        reply.putBoolean(ControlProtocol.NATIVE_ALLOWED, allowed(controller));
        reply.putInt(ControlProtocol.OVERRIDE, value);
        return reply;
    }

    private void applyHandleOverride(Object controller, Object task, Bundle command) throws ReflectiveOperationException {
        if (!command.containsKey(ControlProtocol.PROTECTED)) throw new IllegalArgumentException("Missing window choice");
        boolean enabled = command.getBoolean(ControlProtocol.PROTECTED);
        if (enabled && !allowed(controller)) throw new IllegalStateException("Samsung conditions are unavailable");
        temporary.override(task, 5, enabled ? TemporaryTasks.ON : TemporaryTasks.OFF);
    }

    private boolean selectedTask(Object controller, Object task) throws ReflectiveOperationException {
        if (access.taskPrivacyRequested(task)) return true;
        List<String> selected = selectedApps(controller);
        if (selected.contains((String) basePackage.invoke(task))) return true;
        List<Object> windows = new ArrayList<>();
        allWindows.invoke(task, (Consumer<Object>) windows::add, true);
        for (Object window : windows) {
            if (selected.contains(access.windowPackage(window))) return true;
        }
        return false;
    }

    void contribute(Object window) throws ReflectiveOperationException { temporaryEngine.contribute(window); }

    @Override public boolean selected(Object task) throws ReflectiveOperationException { return protects(task); }

    @Override public boolean drawable(Object window) throws ReflectiveOperationException {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) attrs.get(window);
        return params.type >= 1 && params.type < 1000 && params.width != 0 && params.height != 0
                && !(boolean) fullyTransparent.invoke(window) && transitionAllowed(window)
                && (boolean) ready.invoke(window);
    }

    private boolean transitionAllowed(Object window) throws ReflectiveOperationException {
        if (!transitionFeature.getBoolean(null)) return true;
        Object owner = activity.get(window);
        if (owner == null || !(boolean) inTransition.invoke(owner)) return true;
        if ((boolean) transientHide.invoke(transitionController.get(window), access.task(window))) return false;
        return (boolean) visibleRequested.invoke(owner);
    }

    @Override public boolean intersects(Object window, Object controller) throws IllegalAccessException {
        Rect bounds = (Rect) frame.get(frames.get(window));
        Rect display = (Rect) targetRect.get(controller);
        return display != null && Rect.intersects(bounds, display);
    }

    void removed(Object task) { temporary.remove(task); }
    void changed(Object task) throws ReflectiveOperationException { protects(task); }
    int count() { return temporary.count(); }
    int overrideCount() { return temporary.overrideCount(); }
}
