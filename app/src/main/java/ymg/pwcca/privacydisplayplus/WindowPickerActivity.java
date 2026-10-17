package ymg.pwcca.privacydisplayplus;

import android.os.Bundle;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;

public final class WindowPickerActivity extends FeatureListActivity {
    @Override int pageTitle() { return R.string.protect_window_title; }
    @Override int pageIntro() { return R.string.protect_window_intro; }
    private boolean nativeAllowed;

    @Override void reload() {
        loading();
        control.windows((ok, state) -> {
            if (isDestroyed()) return;
            setBusy(false);
            clearRows();
            if (!usable(ok, state)) { message.setText(R.string.feature_unavailable); return; }
            nativeAllowed = state.getBoolean(ControlProtocol.NATIVE_ALLOWED);
            ArrayList<Bundle> windows = state.getParcelableArrayList(ControlProtocol.ITEMS, Bundle.class);
            if (windows == null || windows.isEmpty()) { message.setText(R.string.no_open_windows); return; }
            message.setText(nativeAllowed ? R.string.windows_ready : R.string.conditions_required);
            for (Bundle window : windows) addWindow(window);
        });
    }

    private void addWindow(Bundle window) {
        String name = window.getString("package");
        if (name == null) return;
        String type = getString(window.getInt("mode") == 5 ? R.string.floating_window : R.string.split_pane);
        String state = getString(window.getBoolean(ControlProtocol.PROTECTED) ? R.string.temporary_selected : R.string.temporary_not_selected);
        if (window.getInt(ControlProtocol.OVERRIDE) == TemporaryTasks.OFF) state = getString(R.string.window_session_off);
        String detail = getString(R.string.window_summary, type, position(window), state);
        if (!window.getBoolean("visible")) detail += "\n" + getString(R.string.window_hidden);
        addRow(label(name), detail, () -> choose(window, label(name)));
    }

    private String position(Bundle window) {
        int[] bounds = window.getIntArray("bounds"), panel = window.getIntArray("display_bounds");
        if (!validBounds(bounds, panel)) return "";
        boolean left = bounds[0] + bounds[2] < panel[0] + panel[2];
        boolean top = bounds[1] + bounds[3] < panel[1] + panel[3];
        if (window.getInt("mode") == 6) {
            return splitPosition(left, top, bounds[2] - bounds[0], panel[2] - panel[0]);
        }
        return floatingPosition(left, top);
    }

    private static boolean validBounds(int[] bounds, int[] panel) {
        return bounds != null && panel != null && bounds.length == 4 && panel.length == 4;
    }

    private String splitPosition(boolean left, boolean top, int width, int panelWidth) {
        boolean sideBySide = width * 10 < panelWidth * 8;
        return getString(sideBySide ? (left ? R.string.position_left : R.string.position_right)
                : (top ? R.string.position_top : R.string.position_bottom));
    }

    private String floatingPosition(boolean left, boolean top) {
        return getString(top ? (left ? R.string.position_upper_left : R.string.position_upper_right)
                : (left ? R.string.position_lower_left : R.string.position_lower_right));
    }

    private void choose(Bundle window, String title) {
        boolean stopping = window.getBoolean(ControlProtocol.PROTECTED);
        if (!stopping && !nativeAllowed) { message.setText(R.string.conditions_required); return; }
        new MaterialAlertDialogBuilder(this).setTitle(title)
                .setMessage(stopping ? R.string.temporary_stop_message : R.string.temporary_start_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(stopping ? R.string.stop_protection : R.string.protect_this_window,
                        (dialog, which) -> apply(window.getString(ControlProtocol.TOKEN), !stopping)).show();
    }

    private void apply(String token, boolean enabled) {
        setBusy(true);
        message.setText(R.string.status_saving);
        control.temporary(token, enabled, (ok, state) -> {
            if (isDestroyed()) return;
            setBusy(false);
            if (!usable(ok, state)) {
                message.setText(R.string.window_changed);
                return;
            }
            if (enabled) {
                setResult(RESULT_OK);
                finish();
            } else reload();
        });
    }
}
