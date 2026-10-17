package ymg.pwcca.privacydisplayplus;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Adds a native CaptionButton inside Samsung's existing freeform handle menu. */
final class SystemUiPopupHook {
    private static final String POLICY = "com.android.wm.shell.windowdecor.policy.";
    private static final String MARKER = "pdplus.handle.toggle";
    private final Class<?> freeform, scroll;
    private final Field taskInfo, taskToken, userId, displayId, keyguard, width, height, animator;
    private final Method buttonColor, ripple;
    private final Constructor<?> buttonConstructor, animatorConstructor;
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    private SystemUiPopupHook(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> base = Class.forName(POLICY + "PopupButtonPolicy", false, loader);
        freeform = Class.forName(POLICY + "FreeformPopupButtonPolicy", false, loader);
        scroll = Class.forName(POLICY + "FreeformScrollPopupButtonPolicy", false, loader);
        taskInfo = field(base, "mTaskInfo", Object.class);
        taskToken = field(taskInfo.getType(), "token", Parcelable.class);
        userId = field(taskInfo.getType(), "userId", int.class);
        displayId = field(taskInfo.getType(), "displayId", int.class);
        keyguard = field(freeform, "mIsKeyguardShowing", boolean.class);
        width = field(base, "mPopupWidth", int.class);
        height = field(base, "mPopupHeight", int.class);
        animator = field(base, "mMenuPopupAnimator", Object.class);
        buttonColor = method(base, "getButtonColor");
        ripple = method(base, "getRippleDrawable", Context.class, View.class);
        buttonConstructor = Class.forName("com.android.wm.shell.windowdecor.widget.CaptionButton", false, loader)
                .getConstructor(Context.class, AttributeSet.class);
        animatorConstructor = Class.forName("com.android.wm.shell.windowdecor.MenuPopupAnimator", false, loader)
                .getConstructor(View.class, int.class, float.class);
    }

    static void install(ClassLoader loader) throws ReflectiveOperationException {
        SystemUiPopupHook integration = new SystemUiPopupHook(loader);
        Class<?> base = Class.forName(POLICY + "PopupButtonPolicy", false, loader);
        Class<?> menu = Class.forName("com.android.wm.shell.windowdecor.HandleMenu", false, loader);
        Class<?> function = Class.forName("kotlin.jvm.functions.Function0", false, loader);
        Method caller = method(menu, "showMenuPopup", boolean.class, boolean.class, View.OnTouchListener.class,
                View.OnClickListener.class, function, int.class, int.class);
        XposedBridge.class.getMethod("deoptimizeMethod", Member.class).invoke(null, caller);
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable()) return;
                integration.onSetup(param.thisObject, (Context) param.args[0], (View) param.args[1]);
            }
        };
        ArrayList<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            hooks.add(XposedBridge.hookMethod(setupMethod(base), hook));
            hooks.add(XposedBridge.hookMethod(setupMethod(integration.scroll), hook));
            if (hooks.contains(null)) throw new IllegalStateException("Handle hook rejected");
        } catch (RuntimeException | ReflectiveOperationException failure) {
            for (XC_MethodHook.Unhook installed : hooks) if (installed != null) installed.unhook();
            throw failure;
        }
        XposedBridge.log("Privacy Display+: popup handle hook installed");
    }

    private static Method setupMethod(Class<?> type) throws NoSuchMethodException {
        return method(type, "setupRootView", Context.class, View.class, View.OnTouchListener.class, View.OnClickListener.class);
    }

    private void onSetup(Object policy, Context context, View view) {
        try {
            if (!eligible(policy, context, view)) return;
            inject(policy, context, (ViewGroup) view);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (failureLogged.compareAndSet(false, true)) {
                XposedBridge.log("Privacy Display+: handle toggle unavailable (" + failure.getClass().getSimpleName() + ")");
            }
        }
    }

    private boolean eligible(Object policy, Context context, View view) throws ReflectiveOperationException {
        if (!freeform.isInstance(policy) || !(view instanceof ViewGroup)) return false;
        if (XposedHelpers.getAdditionalInstanceField(view, MARKER) != null || keyguard.getBoolean(policy)) return false;
        Object info = taskInfo.get(policy);
        if (info == null || userId.getInt(info) != 0 || displayId.getInt(info) != 0) return false;
        try {
            return context.getPackageManager().getPackageInfo(HandleCaller.PACKAGE, 0).getLongVersionCode() == 37;
        } catch (android.content.pm.PackageManager.NameNotFoundException absent) { return false; }
    }

    private void inject(Object policy, Context context, ViewGroup root) throws ReflectiveOperationException {
        ViewGroup container = buttonContainer(context, root);
        if (!(container instanceof LinearLayout)) return;
        int size = context.getResources().getDimensionPixelSize(resource(context, "dimen", "mw_caption_button_width", 0x7e070d01));
        int padding = context.getResources().getDimensionPixelSize(resource(context, "dimen", "mw_caption_button_padding", 0x7e070cfc));
        int menuHeight = height.getInt(policy), oldWidth = width.getInt(policy);
        if (!usableDimensions(size, oldWidth, menuHeight)) return;
        Parcelable token = (Parcelable) taskToken.get(taskInfo.get(policy));
        if (token == null) return;
        ImageButton button = (ImageButton) buttonConstructor.newInstance(context, null);
        button.setId(View.generateViewId());
        button.setPadding(padding, padding, padding, padding);
        button.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        button.setImageTintList((ColorStateList) buttonColor.invoke(policy));
        button.setBackground((Drawable) ripple.invoke(policy, context, button));
        int iconOff = resource(context, "drawable", "privacydisplay_off_00000", 0x7e080f73);
        int iconOn = resource(context, "drawable", "privacydisplay_on_00034", 0x7e080f74);
        Binding binding = new Binding(root, button, context, token, iconOff, iconOn);
        int newWidth = scroll.isInstance(policy) ? oldWidth : Math.addExact(oldWidth, size);
        Object oldAnimator = animator.get(policy);
        Object newAnimator = animatorConstructor.newInstance(root, newWidth, (float) menuHeight);
        try {
            width.setInt(policy, newWidth);
            animator.set(policy, newAnimator);
            View close = container.findViewById(resource(context, "id", "close_window", 0x7e0a02f5));
            int index = close == null ? container.getChildCount() : container.indexOfChild(close);
            container.addView(button, Math.max(0, index), new LinearLayout.LayoutParams(size, menuHeight));
            XposedHelpers.setAdditionalInstanceField(root, MARKER, binding);
            root.addOnAttachStateChangeListener(binding);
            if (root.isAttachedToWindow()) binding.onViewAttachedToWindow(root);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            container.removeView(button);
            width.setInt(policy, oldWidth);
            animator.set(policy, oldAnimator);
            binding.close();
            throw failure;
        }
    }

    private static ViewGroup buttonContainer(Context context, ViewGroup root) {
        View inner = root.findViewById(resource(context, "id", "button_container", 0x7e0a0261));
        return inner instanceof ViewGroup ? (ViewGroup) inner : root;
    }

    private static boolean usableDimensions(int size, int width, int height) {
        return size > 0 && width > 0 && height > 0;
    }

    private static int resource(Context context, String type, String name, int expected) {
        int id = context.getResources().getIdentifier(name, type, HandleCaller.PACKAGE);
        if (id == 0) id = expected;
        if (!name.equals(context.getResources().getResourceEntryName(id))) throw new IllegalStateException("Unsupported menu resources");
        return id;
    }

    private static Field field(Class<?> type, String name, Class<?> value) throws NoSuchFieldException {
        return ReflectivePolicyAccess.field(type, name, value);
    }
    private static Method method(Class<?> type, String name, Class<?>... args) throws NoSuchMethodException {
        return ReflectivePolicyAccess.method(type, name, args);
    }

    private static final class Binding implements View.OnAttachStateChangeListener {
        private final ViewGroup root;
        private final ImageButton button;
        private final Context context;
        private final Parcelable token;
        private final HandleControlClient client;
        private final HandleToggleStyle style;
        private final Drawable iconOff, iconOn;
        private boolean closed, requested, available, connected;

        Binding(ViewGroup root, ImageButton button, Context context, Parcelable token, int iconOff, int iconOn) {
            this.root = root;
            this.button = button;
            this.context = context;
            this.token = token;
            this.iconOff = HandleToggleStyle.icon(context, iconOff);
            this.iconOn = HandleToggleStyle.icon(context, iconOn);
            client = new HandleControlClient(context, Looper.myLooper());
            style = new HandleToggleStyle(button);
            button.setImageDrawable(this.iconOff);
            button.setContentDescription("Privacy Display: checking connection");
            button.setTooltipText("Privacy Display");
            button.setEnabled(false);
            button.setOnClickListener(view -> toggle());
        }

        @Override public void onViewAttachedToWindow(View view) { client.query(token, this::render); }
        @Override public void onViewDetachedFromWindow(View view) { close(); }

        private void render(boolean ok, Bundle state) {
            if (closed) return;
            connected = ok;
            available = ok && state.getBoolean(ControlProtocol.NATIVE_ALLOWED);
            requested = ok && state.getBoolean(ControlProtocol.PROTECTED);
            button.setImageDrawable(requested ? iconOn : iconOff);
            button.setSelected(requested);
            style.apply(requested);
            button.setStateDescription(ok ? (requested ? "On" : "Off") : "Not connected");
            button.setContentDescription(description());
            button.setTooltipText(connected ? (requested ? "Privacy Display: on" : "Privacy Display: off") : "Privacy Display: not connected");
            button.setEnabled(true);
            button.setAlpha(connected && available ? 1.0f : 0.4f);
        }

        private String description() {
            if (!connected) return "Privacy Display: not connected";
            if (!available) return "Privacy Display: turn on conditions in Samsung settings";
            return requested ? "Turn off Privacy Display for this window" : "Turn on Privacy Display for this window";
        }

        private void toggle() {
            if (!connected) { toast("Privacy Display+ is not connected. Enable System Framework and soft reboot after updating."); return; }
            if (!available) { toast("Turn on conditions in Samsung Privacy Display settings first."); return; }
            button.setEnabled(false);
            boolean enable = !requested;
            client.set(token, enable, (ok, state) -> {
                render(ok, state);
                if (closed) return;
                if (!ok) toast("This window changed or Privacy Display+ is unavailable. Reopen the popup menu and try again.");
                else if (!enable) toast("Privacy Display is off for this window. Other Samsung privacy conditions still apply.");
            });
        }

        private void toast(String message) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); }
        void close() {
            if (closed) return;
            closed = true;
            client.close();
            root.removeOnAttachStateChangeListener(this);
            XposedHelpers.removeAdditionalInstanceField(root, MARKER);
            button.setOnClickListener(null);
        }
    }
}
