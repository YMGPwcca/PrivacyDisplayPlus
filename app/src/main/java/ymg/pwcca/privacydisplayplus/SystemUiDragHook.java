package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Adds bounded privacy regions to Samsung's existing drag transaction; does not move windows. */
final class SystemUiDragHook {
    private static final String POSITIONER = "com.android.wm.shell.windowdecor.MultiDisplayVeiledResizeTaskPositioner";
    private final Map<Object, Drag> drags = new WeakHashMap<>();
    private final ThreadLocal<Drag> moving = new ThreadLocal<>();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final AtomicBoolean movementLogged = new AtomicBoolean();
    private final Field decoration, context, handler, ctrlType, token, userId, displayId, partial, scale;
    private final Method taskInfo, taskSurface, windowingMode, stash, setRegions, valid, release, apply, close;
    private final Constructor<?> transactionConstructor;
    private final Class<? extends Parcelable> surfaceClass;

    @SuppressWarnings("unchecked")
    private SystemUiDragHook(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> positioner = Class.forName(POSITIONER, false, loader);
        decoration = positioner.getDeclaredField("windowDecoration");
        context = positioner.getDeclaredField("context");
        handler = positioner.getDeclaredField("handler");
        ctrlType = positioner.getDeclaredField("ctrlType");
        Class<?> wrapper = decoration.getType();
        taskInfo = wrapper.getMethod("getTaskInfo");
        taskSurface = wrapper.getMethod("getTaskSurface");
        stash = wrapper.getMethod("getFreeformStashState");
        scale = stash.getReturnType().getField("mScale");
        Class<?> info = taskInfo.getReturnType();
        token = info.getField("token");
        userId = info.getField("userId");
        displayId = info.getField("displayId");
        partial = info.getField("isPartialPrivacyDisplayApplied");
        windowingMode = info.getMethod("getWindowingMode");
        surfaceClass = (Class<? extends Parcelable>) Class.forName("android.view.SurfaceControl", false, loader);
        Class<?> transaction = Class.forName("android.view.SurfaceControl$Transaction", false, loader);
        transactionConstructor = transaction.getConstructor();
        setRegions = transaction.getMethod("setPrivacyDisplayRegions", surfaceClass, float[][].class);
        valid = surfaceClass.getMethod("isValid");
        release = surfaceClass.getMethod("release");
        apply = transaction.getMethod("apply");
        close = transaction.getMethod("close");
    }

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        SystemUiDragHook integration = new SystemUiDragHook(loader);
        Class<?> positioner = Class.forName(POSITIONER, false, loader);
        Method start = positioner.getMethod("onDragPositioningStart", int.class, int.class, int.class, float.class, float.class);
        Method move = positioner.getMethod("onDragPositioningMove", float.class, float.class, int.class);
        Method end = positioner.getMethod("onDragPositioningEnd", float.class, float.class, int.class);
        Method cancel = positioner.getMethod("onDragPositioningCancel");
        Method dispose = positioner.getMethod("close");
        Method deoptimize = XposedBridge.class.getMethod("deoptimizeMethod", Member.class);
        for (Method method : new Method[]{start, move, end}) deoptimize.invoke(null, method);
        ArrayList<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(start, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) integration.start(param.thisObject);
                }
            }));
            hooks.add(XposedBridge.hookMethod(move, integration.movementHook(false)));
            hooks.add(XposedBridge.hookMethod(end, integration.movementHook(true)));
            XC_MethodHook stopping = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) { integration.stop(param.thisObject); }
            };
            hooks.add(XposedBridge.hookMethod(cancel, stopping));
            hooks.add(XposedBridge.hookMethod(dispose, stopping));
            Class<?> transaction = integration.transactionConstructor.getDeclaringClass();
            hooks.add(XposedBridge.hookMethod(transaction.getMethod("setPosition", integration.surfaceClass, float.class, float.class),
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            integration.position(param.thisObject, param.args[0], (float) param.args[1], (float) param.args[2]);
                        }
                    }));
            if (hooks.contains(null)) throw new IllegalStateException("Drag hook rejected");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            for (XC_MethodHook.Unhook hook : hooks) if (hook != null) hook.unhook();
            throw failure;
        }
        XposedBridge.log("Privacy Display+: floating drag hook installed");
    }

    private XC_MethodHook movementHook(boolean ending) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                moving.set(drags.get(param.thisObject));
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                moving.remove();
                if (ending || param.hasThrowable()) stop(param.thisObject);
            }
        };
    }

    private boolean allowed(Object wrapper) throws ReflectiveOperationException {
        Object info = taskInfo.invoke(wrapper);
        return userId.getInt(info) == 0 && displayId.getInt(info) == 0
                && (int) windowingMode.invoke(info) == 5 && partial.getBoolean(info);
    }

    private void start(Object positioner) {
        stop(positioner);
        try {
            Object wrapper = decoration.get(positioner);
            if (ctrlType.getInt(positioner) != 0 || !allowed(wrapper)) return;
            Context owner = (Context) context.get(positioner);
            Drag drag = new Drag(wrapper, taskSurface.invoke(wrapper), owner, (Handler) handler.get(positioner),
                    (Parcelable) token.get(taskInfo.invoke(wrapper)));
            drags.put(positioner, drag);
            query(drag);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            stop(positioner);
            failed(failure);
        }
    }

    private Intent command(String action, Drag drag) {
        return new Intent(action).setPackage("android")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE)
                .putExtra(ControlProtocol.DRAG_ID, drag.id)
                .putExtra(ControlProtocol.TASK_TOKEN, drag.token);
    }

    private void query(Drag drag) {
        Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
        drag.owner.sendOrderedBroadcast(command(ControlProtocol.DRAG_QUERY, drag), null, options, new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent received) {
                Bundle reply = getResultExtras(false);
                accept(drag, getResultCode() == Activity.RESULT_OK, reply);
            }
        }, drag.handler, Activity.RESULT_CANCELED, null, null);
    }

    private void state(Drag drag, boolean active) {
        try {
            Intent intent = command(ControlProtocol.DRAG_STATE, drag).putExtra(ControlProtocol.DRAG_ACTIVE, active);
            Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
            drag.owner.sendOrderedBroadcast(intent, null, options, null, drag.handler, Activity.RESULT_CANCELED, null, null);
        } catch (RuntimeException failure) { failed(failure); } // The server lease expires if delivery fails.
    }

    private void accept(Drag drag, boolean ok, Bundle reply) {
        try {
            List<FloatingDragSession.Target> targets = targets(reply, drag);
            if (drag.closed) {
                state(drag, false);
                drag.releaseCopies();
                return;
            }
            if (!validReply(ok, reply) || targets.isEmpty()) {
                drag.close();
                return;
            }
            drag.session = new FloatingDragSession(drag.taskSurface, bounds(reply.getIntArray(ControlProtocol.TASK_BOUNDS)), targets);
            drag.handler.postDelayed(drag.renewal, 1000);
            catchUp(drag);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            drag.close();
            failed(failure);
        }
    }

    private static boolean validReply(boolean ok, Bundle reply) {
        return ok && reply != null && reply.getInt(ControlProtocol.VERSION) == ControlProtocol.VERSION_CODE;
    }

    private List<FloatingDragSession.Target> targets(Bundle reply, Drag drag) {
        List<FloatingDragSession.Target> targets = new ArrayList<>();
        if (reply == null) return targets;
        ArrayList<Bundle> rows = reply.getParcelableArrayList(ControlProtocol.ITEMS, Bundle.class);
        if (rows == null) return targets;
        for (Bundle row : rows) {
            FloatingDragSession.Target target = target(row, drag);
            if (target != null && rows.size() <= 8) targets.add(target);
        }
        return targets;
    }

    private FloatingDragSession.Target target(Bundle row, Drag drag) {
        Parcelable surface = row.getParcelable(ControlProtocol.SURFACE, surfaceClass);
        if (surface == null) return null;
        drag.copies.add(surface);
        return new FloatingDragSession.Target(surface, bounds(row.getIntArray(ControlProtocol.WINDOW_BOUNDS)),
                bounds(row.getIntArray(ControlProtocol.DISPLAY_BOUNDS)));
    }

    private static SplitPrivacyRegion.Bounds bounds(int[] coordinates) {
        if (coordinates == null || coordinates.length != 4) throw new IllegalArgumentException("Missing bounds");
        return new SplitPrivacyRegion.Bounds(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
    }

    private void position(Object transaction, Object surface, float x, float y) {
        Drag drag = activeDrag(surface);
        if (drag == null) return;
        try {
            if (!allowed(drag.wrapper)) { drag.close(); return; }
            updatePosition(drag, transaction, x, y);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            drag.close();
            failed(failure);
        }
    }

    private Drag activeDrag(Object surface) {
        Drag drag = moving.get();
        if (drag == null || drag.closed || surface != drag.taskSurface) return null;
        return drag;
    }

    private void updatePosition(Drag drag, Object transaction, float x, float y) throws ReflectiveOperationException {
        drag.x = x;
        drag.y = y;
        drag.scale = scale.getFloat(stash.invoke(drag.wrapper));
        drag.hasPosition = true;
        if (drag.move(transaction) && movementLogged.compareAndSet(false, true)) {
            XposedBridge.log("Privacy Display+: floating drag regions queued with surface movement");
        }
    }

    private void catchUp(Drag drag) throws ReflectiveOperationException {
        if (!drag.hasPosition || !allowed(drag.wrapper)) return;
        Object transaction = transactionConstructor.newInstance();
        try {
            drag.move(transaction);
            apply.invoke(transaction);
        } finally { close.invoke(transaction); }
    }

    private void stop(Object positioner) {
        Drag drag = drags.remove(positioner);
        if (drag != null) drag.close();
    }

    private void failed(Exception failure) {
        if (failureLogged.compareAndSet(false, true)) {
            XposedBridge.log("Privacy Display+: floating drag stopped (" + failure.getClass().getSimpleName() + ")");
        }
    }

    private final class Drag implements FloatingDragSession.Writer {
        final Object wrapper, taskSurface;
        final Context owner;
        final Handler handler;
        final Parcelable token;
        final String id = java.util.UUID.randomUUID().toString();
        final Runnable renewal;
        final List<Parcelable> copies = new ArrayList<>();
        FloatingDragSession session;
        float x, y, scale;
        boolean hasPosition, closed;

        Drag(Object wrapper, Object surface, Context owner, Handler handler, Parcelable token) {
            this.wrapper = wrapper;
            taskSurface = surface;
            this.owner = owner;
            this.handler = handler;
            this.token = token;
            renewal = this::renew;
        }
        private void renew() {
            if (closed) return;
            try {
                if (!allowed(wrapper)) { close(); return; }
                state(this, true);
                handler.postDelayed(renewal, 1000);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                close();
                failed(failure);
            }
        }
        boolean move(Object transaction) throws ReflectiveOperationException {
            return session != null && session.move(transaction, taskSurface, x, y, scale, this);
        }
        @Override public boolean valid(Object surface) throws ReflectiveOperationException {
            return (boolean) valid.invoke(surface);
        }
        @Override public void queue(Object transaction, Object surface, SplitPrivacyRegion region) throws ReflectiveOperationException {
            float[][] data = region == null ? new float[0][] : region.data;
            setRegions.invoke(transaction, surface, (Object) data);
        }
        void close() {
            handler.removeCallbacks(renewal);
            if (!closed) state(this, false);
            closed = true;
            if (session != null) session.close();
            releaseCopies();
        }
        void releaseCopies() {
            for (Parcelable surface : copies) {
                try { release.invoke(surface); }
                catch (ReflectiveOperationException | RuntimeException ignored) { /* Already released or destroyed. */ }
            }
            copies.clear();
        }
    }
}
