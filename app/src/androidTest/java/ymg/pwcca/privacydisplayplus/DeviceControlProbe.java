package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.HandlerThread;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Explicitly invoked device acceptance probe; no production component or dependencies. */
public final class DeviceControlProbe extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { arguments = args; start(); }

    @Override public void onStart() {
        Bundle output = new Bundle();
        try {
            probe(output);
            finish(Activity.RESULT_OK, output);
        } catch (Exception failure) {
            output.putString("failure", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            finish(Activity.RESULT_CANCELED, output);
        }
    }

    private void probe(Bundle output) throws Exception {
        String operation = arguments.getString("operation", "list");
        if ("panel_state".equals(operation)) {
            panelState(output);
            return;
        }
        if ("handle_style".equals(operation)) {
            runOnMainSync(() -> styleProbe(output));
            return;
        }
        if ("handle_thread".equals(arguments.getString("operation"))) {
            callbackThreadProbe(output);
            return;
        }
        if ("handle_denied".equals(arguments.getString("operation"))) {
            Reply denied = send(ControlProtocol.HANDLE_QUERY, null);
            require(denied.code == Activity.RESULT_CANCELED && denied.state == null, "Untrusted handle query was answered");
            output.putBoolean("untrusted_handle_query_denied", true);
            return;
        }
        Bundle status = request(ControlProtocol.QUERY, null);
        require(status.getInt(ControlProtocol.VERSION) == ControlProtocol.VERSION_CODE, "Wrong hook protocol");
        require(status.getBoolean(ControlProtocol.READY) && status.getBoolean(ControlProtocol.HEALTHY), "Hook unavailable");
        Bundle apps = request(ControlProtocol.APPS, null);
        Bundle windows = request(ControlProtocol.WINDOWS, null);
        ArrayList<String> selected = apps.getStringArrayList(ControlProtocol.ITEMS);
        ArrayList<Bundle> rows = windows.getParcelableArrayList(ControlProtocol.ITEMS, Bundle.class);
        require(selected != null && rows != null, "Missing lists");
        output.putInt("selected_app_count", selected.size());
        output.putInt("open_window_count", rows.size());
        output.putInt("protocol", status.getInt(ControlProtocol.VERSION));
        output.putInt("temporary_count", status.getInt(ControlProtocol.TEMP_COUNT));
        output.putInt("window_override_count", status.getInt(ControlProtocol.OVERRIDE_COUNT));
        output.putInt("forced_off_window_count", (int) rows.stream().filter(row -> row.getInt(ControlProtocol.OVERRIDE) == TemporaryTasks.OFF).count());
        if ("list".equals(operation)) return;
        require("enable".equals(operation) || "disable".equals(operation), "Unknown operation");
        boolean enable = "enable".equals(operation);
        Bundle target = uniqueWindow(rows, selected, Integer.parseInt(arguments.getString("task_id", "-1")),
                Integer.parseInt(arguments.getString("window_mode", "5")), arguments.getString("test_package"));
        int expectedBefore = enable ? 0 : 1;
        require(status.getInt(ControlProtocol.TEMP_COUNT) == expectedBefore, "Unexpected existing temporary sessions");
        Bundle command = new Bundle();
        command.putString(ControlProtocol.TOKEN, target.getString(ControlProtocol.TOKEN));
        command.putBoolean(ControlProtocol.PROTECTED, enable);
        request(ControlProtocol.TEMPORARY, command);
        Bundle acknowledged = request(ControlProtocol.QUERY, null);
        require(acknowledged.getInt(ControlProtocol.TEMP_COUNT) == (enable ? 1 : 0), "Temporary count mismatch");
        output.putInt("chosen_task_id", target.getInt("task_id"));
        output.putInt("chosen_window_mode", target.getInt("mode"));
        output.putBoolean("temporary_requested", enable);
        output.putInt("temporary_count", acknowledged.getInt(ControlProtocol.TEMP_COUNT));
        require(selected.equals(request(ControlProtocol.APPS, null).getStringArrayList(ControlProtocol.ITEMS)),
                "Samsung app selection changed");
        output.putBoolean("samsung_selection_unchanged", true);
    }

    private void styleProbe(Bundle output) {
        android.widget.ImageButton button = new android.widget.ImageButton(getTargetContext());
        android.graphics.drawable.GradientDrawable original = new android.graphics.drawable.GradientDrawable();
        original.setColor(android.graphics.Color.TRANSPARENT);
        android.content.res.ColorStateList originalTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE);
        button.setBackground(original);
        button.setImageTintList(originalTint);
        button.setPadding(10, 10, 10, 10);
        HandleToggleStyle style = new HandleToggleStyle(button);
        style.apply(true);
        int size = Math.round(44 * getTargetContext().getResources().getDisplayMetrics().density);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable active = button.getBackground();
        active.setBounds(0, 0, size, size);
        active.draw(new android.graphics.Canvas(bitmap));
        require(android.graphics.Color.alpha(bitmap.getPixel(size / 2, size / 2)) == 255, "Active background is not opaque");
        require(android.graphics.Color.alpha(bitmap.getPixel(0, size / 2)) == 0, "Active background has no outer spacing");
        require(button.getPaddingLeft() == 10 && button.getPaddingTop() == 10, "Background changed icon padding");
        require(active != original, "Active background unchanged");
        style.apply(false);
        require(button.getBackground() == original && button.getImageTintList() == originalTint, "Inactive styling was not restored");
        bitmap.recycle();
        output.putBoolean("active_background_fully_filled", true);
        output.putBoolean("inactive_native_style_restored", true);
        output.putBoolean("active_background_inset", true);
        stylePreview(output);
    }

    private void panelState(Bundle output) throws Exception {
        var manager = getTargetContext().getPackageManager().getApplicationInfo("me.weishu.kernelsu", 0);
        String daemon = manager.nativeLibraryDir + "/libksud.so";
        Process process = new ProcessBuilder(daemon, "debug", "su", "-g").redirectErrorStream(true).start();
        try {
            process.getOutputStream().write("cat /sys/class/lcd/panel/screen_mode\nexit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            process.getOutputStream().close();
            require(process.waitFor(15, TimeUnit.SECONDS), "Panel read timed out");
            String result = new String(process.getInputStream().readNBytes(2048), java.nio.charset.StandardCharsets.UTF_8).trim();
            require(process.exitValue() == 0, "Panel read unavailable");
            output.putString("panel_screen_mode", result);
        } finally { process.destroy(); }
    }

    private void stylePreview(Bundle output) {
        try {
            Context samsung = getTargetContext().createPackageContext("com.android.systemui", Context.CONTEXT_IGNORE_SECURITY);
            int iconId = samsung.getResources().getIdentifier("privacydisplay_off_00000", "drawable", "com.android.systemui");
            int rippleId = samsung.getResources().getIdentifier("mw_caption_button_ripple", "drawable", "com.android.systemui");
            float density = samsung.getResources().getDisplayMetrics().density;
            int size = Math.round(44 * density), gap = Math.round(6 * density), padding = Math.round(10 * density);
            android.widget.LinearLayout row = new android.widget.LinearLayout(samsung);
            row.setBackgroundColor(0xff2c2c2e);
            for (int i = 0; i < 3; i++) {
                android.widget.ImageButton preview = new android.widget.ImageButton(samsung);
                android.graphics.drawable.RippleDrawable ripple = (android.graphics.drawable.RippleDrawable) samsung.getDrawable(rippleId);
                ripple.setLayerInset(0, gap, gap, gap, gap);
                preview.setBackground(ripple);
                preview.setPadding(padding, padding, padding, padding);
                preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                preview.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE));
                preview.setImageDrawable(i == 0 ? samsung.getDrawable(iconId) : HandleToggleStyle.icon(samsung, iconId));
                if (i == 2) new HandleToggleStyle(preview).apply(true);
                row.addView(preview, new android.widget.LinearLayout.LayoutParams(size, size));
            }
            row.measure(android.view.View.MeasureSpec.makeMeasureSpec(size * 3, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY));
            row.layout(0, 0, size * 3, size);
            android.graphics.Bitmap image = android.graphics.Bitmap.createBitmap(size * 3, size, android.graphics.Bitmap.Config.ARGB_8888);
            row.draw(new android.graphics.Canvas(image));
            try (java.io.FileOutputStream file = getTargetContext().openFileOutput("handle-style-preview.png", Context.MODE_PRIVATE)) {
                require(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file), "Preview could not be saved");
            }
            image.recycle();
            output.putString("preview_columns", "Original, updated off, updated on");
        } catch (android.content.pm.PackageManager.NameNotFoundException | java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void callbackThreadProbe(Bundle output) throws Exception {
        HandlerThread viewThread = new HandlerThread("pdplus.popup.view");
        viewThread.start();
        CountDownLatch done = new CountDownLatch(1);
        boolean[] correctThread = {false};
        HandleControlClient client = new HandleControlClient(getTargetContext(), viewThread.getLooper());
        try {
            new Handler(viewThread.getLooper()).post(() -> client.query(null, (ok, state) -> {
                correctThread[0] = Looper.myLooper() == viewThread.getLooper()
                        && Looper.myLooper() != Looper.getMainLooper();
                done.countDown();
                throw new IllegalStateException("Intentional callback boundary regression probe");
            }));
            require(done.await(6, TimeUnit.SECONDS), "Handle callback timed out");
            require(correctThread[0], "Handle callback used the process main thread");
            CountDownLatch alive = new CountDownLatch(1);
            new Handler(viewThread.getLooper()).post(alive::countDown);
            require(alive.await(2, TimeUnit.SECONDS), "View owner thread died after callback failure");
            output.putBoolean("callback_on_view_owner_looper", true);
            output.putBoolean("callback_exception_did_not_kill_view_thread", true);
        } finally {
            client.close();
            viewThread.quitSafely();
        }
    }

    private Bundle uniqueWindow(List<Bundle> rows, List<String> selected, int taskId, int mode, String testPackage) {
        require(mode == 5 || mode == 6, "Unsupported test window mode");
        ArrayList<Bundle> candidates = new ArrayList<>();
        for (Bundle row : rows) {
            if (row.getInt("mode") != mode || selected.contains(row.getString("package"))) continue;
            if (taskId >= 0 && row.getInt("task_id") != taskId) continue;
            if (testPackage != null && !testPackage.equals(row.getString("package"))) continue;
            candidates.add(row);
        }
        require(candidates.size() == 1, "Need exactly one matching unselected test window");
        return candidates.get(0);
    }

    private Bundle request(String action, Bundle values) throws Exception {
        Reply reply = send(action, values);
        require(reply.code == Activity.RESULT_OK && reply.state != null, "Control request rejected");
        return reply.state;
    }

    private record Reply(int code, Bundle state) {}

    private Reply send(String action, Bundle values) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Bundle[] reply = new Bundle[1];
        int[] result = {Activity.RESULT_CANCELED};
        Intent intent = new Intent(action).setPackage("android").addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE);
        if (values != null) intent.putExtras(values);
        Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
        getTargetContext().sendOrderedBroadcast(intent, null, options, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent received) {
                result[0] = getResultCode();
                reply[0] = getResultExtras(false);
                done.countDown();
            }
        }, new Handler(Looper.getMainLooper()), Activity.RESULT_CANCELED, null, null);
        require(done.await(5, TimeUnit.SECONDS), "Control request timed out");
        return new Reply(result[0], reply[0]);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
