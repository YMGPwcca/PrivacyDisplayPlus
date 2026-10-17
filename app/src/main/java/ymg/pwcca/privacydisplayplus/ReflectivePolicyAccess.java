package ymg.pwcca.privacydisplayplus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.graphics.Rect;
import java.util.List;
import java.util.Set;

/** Resolves the complete Samsung schema before installing any hook. */
final class ReflectivePolicyAccess implements PolicyAccess {
    private final Field wmService, serviceExt, privacyController, privacyLock;
    private final Field attrs, securedFlags, privacyMode, privacyOption, appPolicy;
    private final Field packageName, requests, partialTasks, taskSurface;
    private final Method getTask, getDisplayId, getWindowingMode, isSplitScreen, isLeafTask, isValid;
    private final Field taskPrivacyRequested, windowAnimator, bufferSurface;
    private final Field windowFrames, windowFrame;
    private final Method getPendingTransaction, getDisplayContent, getBounds;
    private final Method setRegions;
    private final Field globalLock;
    private final Method requestTraversal;

    ReflectivePolicyAccess(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> window = load(loader, "com.android.server.wm.WindowState");
        Class<?> task = load(loader, "com.android.server.wm.Task");
        Class<?> container = load(loader, "com.android.server.wm.WindowContainer");
        Class<?> animator = load(loader, "com.android.server.wm.WindowStateAnimator");
        Class<?> frames = load(loader, "com.android.server.wm.WindowFrames");
        Class<?> configuration = load(loader, "com.android.server.wm.ConfigurationContainer");
        Class<?> service = load(loader, "com.android.server.wm.WindowManagerService");
        Class<?> extension = load(loader, "com.android.server.wm.WindowManagerServiceExt");
        Class<?> controller = load(loader, "com.android.server.wm.PrivacyDisplayController");
        Class<?> params = load(loader, "android.view.WindowManager$LayoutParams");
        Class<?> surface = load(loader, "android.view.SurfaceControl");
        Class<?> transaction = load(loader, "android.view.SurfaceControl$Transaction");
        wmService = field(window, "mWmService", service);
        globalLock = field(service, "mGlobalLock", Object.class);
        requestTraversal = method(service, "requestTraversal");
        serviceExt = field(service, "mExt", extension);
        privacyController = field(extension, "mPrivacyDisplayController", controller);
        privacyLock = field(controller, "mPrivacyLock", Object.class);
        attrs = field(window, "mAttrs", params);
        securedFlags = field(params, "samsungFlags", int.class);
        privacyMode = field(controller, "mPrivacyDisplayMode", int.class);
        privacyOption = field(controller, "mPrivacyDisplayOption", int.class);
        appPolicy = field(controller, "mShouldApplyAppPolicy", int.class);
        packageName = field(controller, "mPackageName", String.class);
        requests = field(controller, "mApplyRequestPackageList", List.class);
        partialTasks = field(controller, "mPartialPrivacyRequestedTasks", Set.class);
        taskSurface = field(task, "mSurfaceControl", surface);
        getTask = method(window, "getTask");
        getDisplayId = method(window, "getDisplayId");
        getWindowingMode = method(task, "getWindowingMode");
        isSplitScreen = method(task, "inSplitScreenWindowingMode");
        isLeafTask = method(task, "isLeafTask");
        taskPrivacyRequested = field(task, "mPrivacyModeRequested", boolean.class);
        windowAnimator = field(window, "mWinAnimator", animator);
        bufferSurface = field(animator, "mSurfaceControl", surface);
        windowFrames = field(window, "mWindowFrames", frames);
        windowFrame = field(frames, "mFrame", Rect.class);
        getPendingTransaction = method(container, "getPendingTransaction");
        getDisplayContent = method(container, "getDisplayContent");
        getBounds = method(configuration, "getBounds");
        isValid = method(surface, "isValid");
        method(task, "applyPrivacyModeIfNeeded", transaction);
        method(controller, "applyPartialPrivacyDisplayTasksLocked");
        method(transaction, "setPrivacyDisplay", surface, boolean.class);
        setRegions = method(transaction, "setPrivacyDisplayRegions", surface, float[][].class);
    }

    private static Class<?> load(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(name, false, loader);
    }

    static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field result = inheritedField(owner, name);
        if (!type.isAssignableFrom(result.getType())) throw new NoSuchFieldException(name);
        result.setAccessible(true);
        return result;
    }

    private static Field inheritedField(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { /* Search the actual superclass chain. */ }
        }
        throw new NoSuchFieldException(name);
    }

    static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod(name, parameters);
                result.setAccessible(true);
                return result;
            } catch (NoSuchMethodException ignored) { /* Inherited WindowContainer APIs. */ }
        }
        throw new NoSuchMethodException(name);
    }

    @Override public Object controller(Object w) throws IllegalAccessException {
        return controllerForService(wmService.get(w));
    }
    Object controllerForService(Object service) throws IllegalAccessException {
        return privacyController.get(serviceExt.get(service));
    }
    Object globalLock(Object service) throws IllegalAccessException { return globalLock.get(service); }
    @Override public String windowPackage(Object window) throws IllegalAccessException {
        return ((android.view.WindowManager.LayoutParams) attrs.get(window)).packageName;
    }
    @Override public int userId(Object task) throws ReflectiveOperationException {
        return field(task.getClass(), "mUserId", int.class).getInt(task);
    }
    Object windowService(Object window) throws IllegalAccessException { return wmService.get(window); }
    void requestTraversal(Object service) throws ReflectiveOperationException {
        synchronized (globalLock.get(service)) { requestTraversal.invoke(service); }
    }
    @Override public Object lock(Object c) throws IllegalAccessException { return privacyLock.get(c); }
    @Override public Object task(Object w) throws ReflectiveOperationException { return getTask.invoke(w); }
    @Override public int displayId(Object w) throws ReflectiveOperationException { return (int) getDisplayId.invoke(w); }
    @Override public int windowingMode(Object t) throws ReflectiveOperationException { return (int) getWindowingMode.invoke(t); }
    @Override public boolean splitScreen(Object t) throws ReflectiveOperationException { return (boolean) isSplitScreen.invoke(t); }
    @Override public boolean leafTask(Object t) throws ReflectiveOperationException { return (boolean) isLeafTask.invoke(t); }
    @Override public Object bufferSurface(Object window) throws IllegalAccessException {
        return bufferSurface.get(windowAnimator.get(window));
    }
    @Override public boolean taskPrivacyRequested(Object task) throws IllegalAccessException {
        return taskPrivacyRequested.getBoolean(task);
    }
    @Override public SplitPrivacyRegion privacyRegion(Object window) throws ReflectiveOperationException {
        SplitPrivacyRegion.Bounds display = displayBounds(window);
        return display == null ? null : SplitPrivacyRegion.create(windowBounds(window), display);
    }
    @Override public SplitPrivacyRegion.Bounds windowBounds(Object window) throws IllegalAccessException {
        return regionBounds((Rect) windowFrame.get(windowFrames.get(window)));
    }
    @Override public SplitPrivacyRegion.Bounds displayBounds(Object window) throws ReflectiveOperationException {
        Object display = getDisplayContent.invoke(window);
        if (display == null) return null;
        return regionBounds((Rect) getBounds.invoke(display));
    }
    private static SplitPrivacyRegion.Bounds regionBounds(Rect rect) {
        return new SplitPrivacyRegion.Bounds(rect.left, rect.top, rect.right, rect.bottom);
    }
    @Override public void queuePrivacyRegion(Object window, Object surface, SplitPrivacyRegion region)
            throws ReflectiveOperationException {
        float[][] data = region == null ? new float[0][] : region.data;
        setRegions.invoke(getPendingTransaction.invoke(window), surface, (Object) data);
    }
    @Override public Object surface(Object t) throws IllegalAccessException { return taskSurface.get(t); }
    @Override public boolean validSurface(Object s) throws ReflectiveOperationException { return (boolean) isValid.invoke(s); }
    @Override public int mode(Object c) throws IllegalAccessException { return privacyMode.getInt(c); }
    @Override public int option(Object c) throws IllegalAccessException { return privacyOption.getInt(c); }
    @Override public int samsungFlags(Object w) throws IllegalAccessException { return securedFlags.getInt(attrs.get(w)); }
    @Override public int appPolicy(Object c) throws IllegalAccessException { return appPolicy.getInt(c); }
    @Override public void appPolicy(Object c, int v) throws IllegalAccessException { appPolicy.setInt(c, v); }
    @Override public String packageName(Object c) throws IllegalAccessException { return (String) packageName.get(c); }
    @Override public void packageName(Object c, String v) throws IllegalAccessException { packageName.set(c, v); }
    @Override public List<?> requests(Object c) throws IllegalAccessException { return (List<?>) requests.get(c); }
    @SuppressWarnings("unchecked")
    @Override public Set<Object> partialTasks(Object c) throws IllegalAccessException { return (Set<Object>) partialTasks.get(c); }
}
