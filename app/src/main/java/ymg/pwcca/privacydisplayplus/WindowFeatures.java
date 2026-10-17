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
    private final WindowSurfacePrivacy surfaces;
    private final TemporaryTasks temporary = new TemporaryTasks();
    private final Field removing, ownerUser, appPolicyChange, valuesByUser, displayId;
    private final Field temporaryOff, fixedOff, targetRect, frames, frame, attrs;
    private final Field activity, transitionController, transitionFeature;
    private final Method allWindows, attached, displayContent, basePackage, isApp, taskBounds, ready;
    private final Method fullyTransparent, inTransition, transientHide, visibleRequested;
    private final Class<?> taskTokenClass;
    private final Method fromTaskToken;

    WindowFeatures(ClassLoader loader, ReflectivePolicyAccess access, WindowSurfacePrivacy split)
            throws ReflectiveOperationException {
        this.access = access;
        surfaces = split;
        temporaryEngine = new TemporaryProtectionEngine(access, split, this);
        Class<?> task = Class.forName("com.android.server.wm.Task", false, loader);
        Class<?> controller = Class.forName("com.android.server.wm.PrivacyDisplayController", false, loader);
        Class<?> window = Class.forName("com.android.server.wm.WindowState", false, loader);
        Class<?> changes = Class.forName("com.samsung.android.server.packagefeature.PackageFeatureUserChange", false, loader);
        Class<?> activityClass = Class.forName("com.android.server.wm.ActivityRecord", false, loader);
        Class<?> transitions = Class.forName("com.android.server.wm.TransitionController", false, loader);
        removing = field(task, "mRemoving", boolean.class);
        ownerUser = field(task, "mUserId", int.class);
        allWindows = method(task, "forAllWindows", Consumer.class, boolean.class);
        attached = method(task, "isAttached");
        displayContent = method(task, "getDisplayContent");
        displayId = field(displayContent.getReturnType(), "mDisplayId", int.class);
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

    boolean protects(Object task) throws ReflectiveOperationException {
        return decision(task) == TemporaryTasks.ON;
    }

    private int decision(Object task) throws ReflectiveOperationException {
        if (task == null || !temporary.known(task)) return TemporaryTasks.FOLLOW;
        if (!openTask(task)) { temporary.remove(task); return TemporaryTasks.FOLLOW; }
        return temporary.decision(task, regionalMode(task));
    }

    ProtectionOptions optionsFor(Object window, ProtectionOptions options) throws ReflectiveOperationException {
        return taskOptions(access.task(window), options);
    }

    private ProtectionOptions taskOptions(Object task, ProtectionOptions options) throws ReflectiveOperationException {
        return switch (decision(task)) {
            case TemporaryTasks.ON -> ProtectionOptions.BOTH;
            case TemporaryTasks.OFF -> ProtectionOptions.NONE;
            default -> options;
        };
    }

    boolean suppresses(Object window) throws ReflectiveOperationException {
        return decision(access.task(window)) == TemporaryTasks.OFF;
    }

    Bundle handle(Object controller, Bundle command, boolean mutate) throws ReflectiveOperationException {
        Object task = popupTask(command);
        if (mutate) applyHandleOverride(controller, task, command);
        Bundle reply = new Bundle();
        int value = decision(task);
        boolean requested = value == TemporaryTasks.ON || (value == TemporaryTasks.FOLLOW && selectedTask(controller, task));
        reply.putBoolean(ControlProtocol.PROTECTED, requested);
        reply.putBoolean(ControlProtocol.NATIVE_ALLOWED, allowed(controller));
        reply.putInt(ControlProtocol.OVERRIDE, value);
        return reply;
    }

    private Object popupTask(Bundle command) throws ReflectiveOperationException {
        if (command == null) throw new IllegalArgumentException("Missing window");
        Parcelable token = command.getParcelable(ControlProtocol.TASK_TOKEN, Parcelable.class);
        if (!taskTokenClass.isInstance(token)) throw new IllegalArgumentException("Invalid window token");
        Object task = fromTaskToken.invoke(null, token);
        if (!validPopup(task)) {
            throw new IllegalArgumentException("Popup closed or changed");
        }
        return task;
    }

    private boolean validPopup(Object task) throws ReflectiveOperationException {
        return task != null && openTask(task) && regionalMode(task) == 5 && hasClientWindow(task);
    }

    Bundle drag(Object controller, Bundle command, ProtectionOptions options) throws ReflectiveOperationException {
        Object task = popupTask(command);
        String id = dragId(command);
        if (!allowed(controller) || !access.taskPrivacyRequested(task)) throw new IllegalStateException("Not protected");
        options = taskOptions(task, options);
        ArrayList<Bundle> targets = new ArrayList<>();
        for (WindowSurfacePrivacy.DragTarget target : surfaces.dragTargets(task, options)) {
            Bundle row = new Bundle();
            row.putParcelable(ControlProtocol.SURFACE, (Parcelable) target.surface());
            row.putIntArray(ControlProtocol.WINDOW_BOUNDS, coordinates(target.window()));
            row.putIntArray(ControlProtocol.DISPLAY_BOUNDS, coordinates(target.display()));
            row.putFloat(ControlProtocol.CORNER_RADIUS, target.cornerRadius());
            targets.add(row);
        }
        Bundle reply = new Bundle();
        reply.putParcelableArrayList(ControlProtocol.ITEMS, targets);
        if (!targets.isEmpty()) surfaces.beginDrag(task, id);
        Rect bounds = (Rect) taskBounds.invoke(task);
        reply.putIntArray(ControlProtocol.TASK_BOUNDS, new int[]{bounds.left, bounds.top, bounds.right, bounds.bottom});
        return reply;
    }

    Bundle dragState(Object controller, Bundle command) throws ReflectiveOperationException {
        String id = dragId(command);
        Parcelable token = command.getParcelable(ControlProtocol.TASK_TOKEN, Parcelable.class);
        if (!taskTokenClass.isInstance(token)) throw new IllegalArgumentException("Invalid window token");
        Object task = fromTaskToken.invoke(null, token);
        if (task == null) return new Bundle();
        boolean active = command.getBoolean(ControlProtocol.DRAG_ACTIVE);
        if (!active) surfaces.endDrag(task, id);
        else if (regionalMode(task) == 5 && allowed(controller) && access.taskPrivacyRequested(task)) surfaces.renewDrag(task, id);
        return new Bundle();
    }

    private static String dragId(Bundle command) {
        String id = command.getString(ControlProtocol.DRAG_ID);
        if (id == null || id.length() > 64) throw new IllegalArgumentException("Missing drag identity");
        return id;
    }

    private static int[] coordinates(SplitPrivacyRegion.Bounds bounds) {
        return new int[]{bounds.left(), bounds.top(), bounds.right(), bounds.bottom()};
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

    void removed(Object task) { temporary.remove(task); surfaces.endDrag(task); }
    void changed(Object task) throws ReflectiveOperationException { protects(task); }
    int count() { return temporary.count(); }
    int overrideCount() { return temporary.overrideCount(); }
}
