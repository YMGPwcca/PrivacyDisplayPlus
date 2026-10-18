package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Uses native shade state, not accessibility, touch interception, or periodic polling. */
final class SystemUiShadeHook {
    private final Field contextField, statusField, dozingField, bouncerField;
    private final Field panelStateField, eventFractionField, eventExpandedField;
    private final Binder source = new Binder();
    private Context context;
    private Handler handler;
    private boolean acknowledged, sending, resend, reportedFailure;
    private int desiredPanels, acknowledgedPanels;
    private float fraction;
    private boolean expanded, dozing, bouncerShowing;
    private int panelState = -1, statusBarState;

    private SystemUiShadeHook(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> controller = Class.forName("com.android.systemui.shade.NotificationShadeWindowControllerImpl", false, loader);
        Class<?> state = Class.forName("com.android.systemui.shade.NotificationShadeWindowState", false, loader);
        Class<?> panel = Class.forName("com.android.systemui.shade.SecPanelSplitHelper", false, loader);
        Class<?> event = Class.forName("com.android.systemui.shade.ShadeExpansionChangeEvent", false, loader);
        contextField = ReflectivePolicyAccess.field(controller, "mContext", Context.class);
        panelStateField = ReflectivePolicyAccess.field(panel, "currentState", int.class);
        eventFractionField = ReflectivePolicyAccess.field(event, "fraction", float.class);
        eventExpandedField = ReflectivePolicyAccess.field(event, "expanded", boolean.class);
        statusField = ReflectivePolicyAccess.field(state, "statusBarState", int.class);
        dozingField = ReflectivePolicyAccess.field(state, "dozing", boolean.class);
        bouncerField = ReflectivePolicyAccess.field(state, "bouncerShowing", boolean.class);
    }

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        SystemUiShadeHook integration = new SystemUiShadeHook(loader);
        Class<?> controller = integration.contextField.getDeclaringClass();
        Class<?> state = integration.statusField.getDeclaringClass();
        Class<?> panel = integration.panelStateField.getDeclaringClass();
        Class<?> event = integration.eventFractionField.getDeclaringClass();
        Class<?> direction = Class.forName("com.android.systemui.shade.PanelSlideEventHandler$Direction", false, loader);
        Method apply = ReflectivePolicyAccess.method(controller, "apply", state);
        Method update = ReflectivePolicyAccess.method(panel, "updateState", int.class);
        Method expansion = ReflectivePolicyAccess.method(panel, "onPanelExpansionChanged", event);
        Method deoptimize = XposedBridge.class.getMethod("deoptimizeMethod", Member.class);
        for (Method target : new Method[]{apply, update, expansion,
                ReflectivePolicyAccess.method(panel, "slide", float.class, direction, boolean.class),
                ReflectivePolicyAccess.method(panel, "slide$1", int.class),
                ReflectivePolicyAccess.method(Class.forName("com.android.systemui.shade.NotificationPanelViewController",
                        false, loader), "updateExpansionAndVisibility")}) {
            deoptimize.invoke(null, target);
        }
        List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(apply, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) integration.observeWindow(param.thisObject, param.args[0]);
                }
            }));
            hooks.add(XposedBridge.hookMethod(update, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) integration.observePanel(param.thisObject, null);
                }
            }));
            hooks.add(XposedBridge.hookMethod(expansion, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) integration.observePanel(param.thisObject, param.args[0]);
                }
            }));
            if (hooks.contains(null)) throw new IllegalStateException("System UI shade hook rejected");
        } catch (RuntimeException | Error failure) {
            for (XC_MethodHook.Unhook hook : hooks) if (hook != null) hook.unhook();
            throw failure;
        }
        XposedBridge.log("Privacy Display+: Samsung notification panel state hook installed");
    }

    private void observeWindow(Object controller, Object state) {
        try {
            if (context == null) initialize((Context) contextField.get(controller));
            statusBarState = statusField.getInt(state);
            dozing = dozingField.getBoolean(state);
            bouncerShowing = bouncerField.getBoolean(state);
            refresh(false);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failed(failure);
        }
    }

    private void observePanel(Object panel, Object event) {
        try {
            if (event != null) {
                fraction = eventFractionField.getFloat(event);
                expanded = eventExpandedField.getBoolean(event);
            }
            panelState = panelStateField.getInt(panel);
            refresh(false);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failed(failure);
        }
    }

    private void refresh(boolean force) {
        if (context == null) return; // Wait for native lockscreen/doze state before publishing.
        desiredPanels = (NotificationShadePrivacy.notificationCenterOpen(fraction, expanded, panelState,
                statusBarState, dozing, bouncerShowing) ? 1 : 0)
                | (NotificationShadePrivacy.quickSettingsOpen(fraction, expanded, panelState,
                statusBarState, dozing, bouncerShowing) ? 2 : 0);
        publish(force);
    }

    private void initialize(Context owner) {
        Handler callbackHandler = new Handler(Looper.myLooper());
        owner.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent intent) { refresh(true); }
        }, new IntentFilter(ControlProtocol.SHADE_QUERY), "android.permission.MANAGE_ACTIVITY_TASKS",
                callbackHandler, Context.RECEIVER_EXPORTED);
        handler = callbackHandler;
        context = owner;
    }

    private void publish(boolean force) {
        if (sending) { resend |= force; return; }
        if (!force && acknowledged && acknowledgedPanels == desiredPanels) return;
        int panels = desiredPanels;
        Bundle extras = new Bundle();
        extras.putBinder(ControlProtocol.SHADE_OWNER, source);
        extras.putBoolean(ControlProtocol.NOTIFICATION_CENTER_OPEN, (panels & 1) != 0);
        extras.putBoolean(ControlProtocol.QUICK_SETTINGS_OPEN, (panels & 2) != 0);
        Intent intent = new Intent(ControlProtocol.SHADE_STATE).setPackage("android")
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE).putExtras(extras);
        Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
        sending = true;
        try {
            context.sendOrderedBroadcast(intent, null, options, new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent received) {
                    Bundle reply = getResultExtras(false);
                    acknowledged = getResultCode() == Activity.RESULT_OK && reply != null
                            && reply.getInt(ControlProtocol.VERSION) == ControlProtocol.VERSION_CODE;
                    acknowledgedPanels = panels;
                    sending = false;
                    boolean forceAgain = resend;
                    resend = false;
                    if (forceAgain || desiredPanels != panels) publish(forceAgain);
                }
            }, handler, Activity.RESULT_CANCELED, null, null);
        } catch (RuntimeException failure) {
            sending = false;
            acknowledged = false;
            failed(failure);
        }
    }

    private void failed(Throwable failure) {
        if (reportedFailure) return;
        reportedFailure = true;
        XposedBridge.log("Privacy Display+: shade state unavailable (" + failure.getClass().getSimpleName() + ")");
    }
}
