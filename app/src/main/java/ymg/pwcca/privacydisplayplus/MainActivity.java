package ymg.pwcca.privacydisplayplus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ControlClient control;
    private MaterialSwitch floatingToggle, splitToggle, secureEntryMaximumToggle;
    private SettingsChoiceMenu notificationCenterLevel, quickSettingsLevel, lockScreenLevel;
    private PanelPrivacyLevel notificationCenterMode = PanelPrivacyLevel.OFF;
    private PanelPrivacyLevel quickSettingsMode = PanelPrivacyLevel.OFF;
    private PanelPrivacyLevel lockScreenMode = PanelPrivacyLevel.OFF;
    private Button reboot;
    private TextView statusTitle, statusDetail, rebootStatus;
    private boolean rendering, busy;
    private int settingsGeneration;
    private final Runnable rootStateListener = () -> runOnUiThread(this::renderRootState);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        DynamicColors.applyToActivityIfAvailable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setSystemBarIconAppearance();
        setContentView(R.layout.activity_main);
        control = new ControlClient(this);
        floatingToggle = findViewById(R.id.popup_toggle);
        splitToggle = findViewById(R.id.split_toggle);
        secureEntryMaximumToggle = findViewById(R.id.secure_entry_maximum_toggle);
        reboot = findViewById(R.id.soft_reboot);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        rebootStatus = findViewById(R.id.reboot_status);
        actionRow(R.id.app_rules, R.string.app_rules_title, R.string.app_rules_description,
                () -> startActivity(new android.content.Intent(this, AppRulesActivity.class)));
        findViewById(R.id.screen).setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        notificationCenterLevel = setupPanelLevel(R.id.notification_center_level, true);
        quickSettingsLevel = setupPanelLevel(R.id.quick_settings_level, false);
        lockScreenLevel = new SettingsChoiceMenu(findViewById(R.id.lock_screen_level),
                R.string.lock_screen_title, R.string.lock_screen_description,
                getResources().getStringArray(R.array.panel_privacy_levels), position -> {
            if (rendering) return;
            lockScreenMode = PanelPrivacyLevel.fromMenu(position);
            saveAndApply(currentChoice());
        });
        renderSavedChoice();
        floatingToggle.setOnCheckedChangeListener((view, checked) -> {
            if (!rendering) saveAndApply(currentChoice());
        });
        splitToggle.setOnCheckedChangeListener((view, checked) -> {
            if (!rendering) saveAndApply(currentChoice());
        });
        secureEntryMaximumToggle.setOnCheckedChangeListener((view, checked) -> {
            if (!rendering) saveAndApply(currentChoice());
        });
        reboot.setOnClickListener(view -> confirmSoftReboot());
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != R.id.help) return false;
            showHelp();
            return true;
        });
        setBusy(false);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!supported()) {
            status(R.string.status_unsupported, R.string.status_unsupported_detail, true);
            return;
        }
        if (!busy) control.query(this::renderRuntime);
    }

    @Override protected void onStart() {
        super.onStart();
        RootCommandRunner.state().add(rootStateListener);
    }

    @Override protected void onStop() {
        RootCommandRunner.state().remove(rootStateListener);
        super.onStop();
    }

    @Override protected void onDestroy() {
        control.close();
        worker.shutdown();
        super.onDestroy();
    }

    private boolean supported() { return FirmwareProfile.supports(Build.VERSION.SDK_INT, Build.FINGERPRINT); }

    private void setSystemBarIconAppearance() {
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        var bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(light);
        bars.setAppearanceLightNavigationBars(light);
    }

    private void renderSavedChoice() {
        ProtectionOptions options = ModulePreferences.options(this);
        rendering = true;
        floatingToggle.setChecked(options.floating);
        splitToggle.setChecked(options.split);
        secureEntryMaximumToggle.setChecked(options.secureEntryMaximum);
        notificationCenterMode = PanelPrivacyLevel.fromSaved(options.notificationCenter, options.notificationCenterMaximum);
        quickSettingsMode = PanelPrivacyLevel.fromSaved(options.quickSettings, options.quickSettingsMaximum);
        lockScreenMode = PanelPrivacyLevel.fromSaved(options.lockScreen, options.lockScreenMaximum);
        notificationCenterLevel.setSelection(notificationCenterMode.menuIndex);
        quickSettingsLevel.setSelection(quickSettingsMode.menuIndex);
        lockScreenLevel.setSelection(lockScreenMode.menuIndex);
        rendering = false;
    }

    private SettingsChoiceMenu setupPanelLevel(int id, boolean center) {
        int title = center ? R.string.notification_center_title : R.string.quick_settings_title;
        int description = center ? R.string.notification_center_description : R.string.quick_settings_description;
        return new SettingsChoiceMenu(findViewById(id), title, description,
                getResources().getStringArray(R.array.panel_privacy_levels), position -> {
            if (rendering) return;
            PanelPrivacyLevel selected = PanelPrivacyLevel.fromMenu(position);
            if (center) notificationCenterMode = selected;
            else quickSettingsMode = selected;
            saveAndApply(currentChoice());
        });
    }


    private ProtectionOptions currentChoice() {
        return ModulePreferences.options(this)
                .withDefaults(floatingToggle.isChecked(), splitToggle.isChecked())
                .withPanelPrivacy(notificationCenterMode.enabled, quickSettingsMode.enabled,
                        notificationCenterMode.maximum, quickSettingsMode.maximum)
                .withLockScreenPrivacy(lockScreenMode.enabled, lockScreenMode.maximum)
                .withSecureEntryMaximum(secureEntryMaximumToggle.isChecked());
    }

    private void actionRow(int id, int title, int detail, Runnable action) {
        android.view.View row = findViewById(id);
        ((TextView) row.findViewById(R.id.action_title)).setText(title);
        ((TextView) row.findViewById(R.id.action_detail)).setText(detail);
        row.setContentDescription(getString(title) + ". " + getString(detail));
        row.setOnClickListener(view -> { if (!busy) action.run(); });
    }

    private void saveAndApply(ProtectionOptions desired) {
        int request = ++settingsGeneration;
        setBusy(true);
        worker.execute(() -> {
            ProtectionOptions committed = ModulePreferences.update(this,
                    previous -> previous.withDefaults(desired.floating, desired.split)
                            .withPanelPrivacy(desired.notificationCenter, desired.quickSettings,
                                    desired.notificationCenterMaximum, desired.quickSettingsMaximum)
                            .withLockScreenPrivacy(desired.lockScreen, desired.lockScreenMaximum)
                            .withSecureEntryMaximum(desired.secureEntryMaximum));
            boolean saved = committed != null;
            runOnUiThread(() -> {
                if (isDestroyed() || request != settingsGeneration) return;
                if (!saved) {
                    renderSavedChoice();
                    status(R.string.status_save_failed, R.string.status_save_failed_detail, true);
                    setBusy(false);
                    return;
                }
                control.set(committed, (acknowledged, state) -> {
                    if (isDestroyed() || request != settingsGeneration) return;
                    renderRuntime(acknowledged, state);
                    setBusy(false);
                });
            });
        });
    }

    private void renderRuntime(boolean acknowledged, Bundle state) {
        if (isDestroyed()) return;
        if (!ControlProtocol.hasOptions(state) || state.getInt(ControlProtocol.VERSION) != ControlProtocol.VERSION_CODE) {
            status(R.string.status_pending, R.string.status_pending_detail, false);
            return;
        }
        if (!state.getBoolean(ControlProtocol.HEALTHY)) {
            status(R.string.status_failed, R.string.status_failed_detail, true);
            return;
        }
        if (!matchesSaved(acknowledged, state)) {
            status(R.string.status_failed, R.string.status_not_applied_detail, true);
            return;
        }
        ProtectionOptions options = ControlProtocol.readOptions(state);
        if ((options.notificationCenter || options.quickSettings || options.lockScreen)
                && !state.getBoolean(ControlProtocol.SHADE_READY)) {
            status(R.string.status_pending, R.string.status_pending_detail, false);
            return;
        }
        boolean enabled = options.anyEnabled() || options.hasMaximumRules() || options.secureEntryMaximum
                || state.getInt(ControlProtocol.OVERRIDE_COUNT) > 0;
        status(enabled ? R.string.status_on : R.string.status_off,
                enabled ? R.string.status_on_detail : R.string.status_off_detail, false);
    }

    private boolean matchesSaved(boolean acknowledged, Bundle state) {
        return acknowledged && state.getBoolean(ControlProtocol.READY)
                && ControlProtocol.readOptions(state).sameAs(ModulePreferences.options(this));
    }

    private void status(int title, int detail, boolean error) {
        statusTitle.setText(title);
        statusDetail.setText(detail);
        statusTitle.setTextColor(MaterialColors.getColor(statusTitle, error
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnPrimaryContainer));
    }

    private void setBusy(boolean value) {
        busy = value;
        // Saving is asynchronous; keep controls visually stable while the worker serializes changes.
        boolean available = !RootCommandRunner.state().isRunning() && supported();
        floatingToggle.setEnabled(available);
        splitToggle.setEnabled(available);
        secureEntryMaximumToggle.setEnabled(available);
        notificationCenterLevel.setEnabled(available);
        quickSettingsLevel.setEnabled(available);
        lockScreenLevel.setEnabled(available);
        findViewById(R.id.app_rules).setEnabled(available);
        reboot.setEnabled(available && !RootCommandRunner.state().isPending());
    }

    private void confirmSoftReboot() {
        if (busy) return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.reboot_dialog_title)
                .setMessage(R.string.reboot_dialog_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.soft_reboot, (dialog, which) -> requestSoftReboot()).show();
    }

    private String getPackageManagerVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException failure) { throw new IllegalStateException(failure); }
    }

    private void showHelp() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.help_title)
                .setMessage(getString(R.string.help_message, getPackageManagerVersion()))
                .setPositiveButton(R.string.understood, null).show();
    }

    private void requestSoftReboot() {
        String daemon = managerDaemon();
        reboot.setEnabled(false);
        worker.execute(() -> new RootCommandRunner().softReboot(daemon));
    }

    private void renderRootState() {
        if (isDestroyed()) return;
        RootOperationState state = RootCommandRunner.state();
        RootCommandRunner.Result outcome = state.result();
        if (state.isRunning()) rebootStatus.setText(R.string.reboot_waiting);
        else if (outcome != null) rebootStatus.setText(rebootMessage(outcome));
        else rebootStatus.setText(R.string.reboot_root_hint);
        setBusy(busy);
    }

    private String managerDaemon() {
        try {
            ApplicationInfo manager = getPackageManager().getApplicationInfo("me.weishu.kernelsu", 0);
            if (manager.nativeLibraryDir == null) return null;
            return new File(manager.nativeLibraryDir, "libksud.so").getAbsolutePath();
        } catch (PackageManager.NameNotFoundException missing) { return null; }
    }

    private static int rebootMessage(RootCommandRunner.Result result) {
        return switch (result) {
            case REQUESTED -> R.string.reboot_requested;
            case ROOT_UNAVAILABLE -> R.string.reboot_unavailable;
            case DENIED -> R.string.reboot_denied;
            case UNSUPPORTED -> R.string.reboot_unsupported;
            case TIMED_OUT -> R.string.reboot_timeout;
            case FAILED -> R.string.reboot_failed;
            case ALREADY_RUNNING -> R.string.reboot_timeout;
        };
    }
}
