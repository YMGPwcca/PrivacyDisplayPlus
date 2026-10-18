package ymg.pwcca.privacydisplayplus;

import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppRulesActivity extends FeatureListActivity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    @Override int pageTitle() { return R.string.app_rules_title; }
    @Override int pageIntro() { return R.string.app_rules_intro; }

    @Override void reload() {
        loading();
        control.apps((ok, state) -> {
            if (isDestroyed()) return;
            setBusy(false);
            clearRows();
            if (!usable(ok, state)) { message.setText(R.string.feature_unavailable); return; }
            ArrayList<String> names = state.getStringArrayList(ControlProtocol.ITEMS);
            if (names == null || names.isEmpty()) { message.setText(R.string.no_selected_apps); return; }
            names.sort(Comparator.comparing(this::label, String.CASE_INSENSITIVE_ORDER));
            ProtectionOptions saved = ModulePreferences.options(this);
            boolean applied = saved.sameAs(ControlProtocol.readOptions(state));
            message.setText(applied ? R.string.rules_ready : R.string.status_not_applied_detail);
            for (String name : names) {
                AppRule rule = saved.appRules.getOrDefault(name, AppRule.INHERIT);
                addRow(label(name), summary(rule), () -> edit(name));
            }
        });
    }

    private String summary(AppRule rule) {
        return getString(R.string.rule_summary, choice(rule.floating()), choice(rule.split()))
                + "\n" + getString(rule.maximum() ? R.string.maximum_rule_on : R.string.maximum_rule_default);
    }

    private String choice(int value) {
        return getString(switch (value) {
            case AppRule.WINDOW -> R.string.rule_window;
            case AppRule.FULL_SCREEN -> R.string.rule_full_screen;
            default -> R.string.rule_default;
        });
    }

    private void edit(String name) {
        ProtectionOptions saved = ModulePreferences.options(this);
        AppRule rule = saved.appRules.getOrDefault(name, AppRule.INHERIT);
        View content = getLayoutInflater().inflate(R.layout.dialog_app_rule, null);
        MaterialSwitch maximum = content.findViewById(R.id.rule_maximum);
        maximum.setChecked(rule.maximum());
        int[] choices = {rule.floating(), rule.split()};
        setupChoice(content.findViewById(R.id.rule_floating), R.string.floating_title, saved.floating, choices, 0);
        setupChoice(content.findViewById(R.id.rule_split), R.string.split_title, saved.split, choices, 1);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(this).setTitle(label(name)).setView(scroll)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        save(name, new AppRule(choices[0], choices[1], maximum.isChecked())))
                .show();
    }

    private void setupChoice(View view, int title, boolean fallback, int[] values, int index) {
        String[] labels = {getString(R.string.rule_default_value, choice(fallback ? AppRule.WINDOW : AppRule.FULL_SCREEN)),
                choice(AppRule.WINDOW), choice(AppRule.FULL_SCREEN)};
        String[] valueLabels = {choice(AppRule.DEFAULT), choice(AppRule.WINDOW), choice(AppRule.FULL_SCREEN)};
        SettingsChoiceMenu menu = new SettingsChoiceMenu(view, title, 0, labels, valueLabels,
                position -> values[index] = position);
        menu.setSelection(values[index]);
    }

    private void save(String name, AppRule rule) {
        setBusy(true);
        message.setText(R.string.status_saving);
        worker.execute(() -> {
            ProtectionOptions desired = ModulePreferences.update(this, previous -> previous.withRule(name, rule));
            boolean saved = desired != null;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (!saved) { setBusy(false); message.setText(R.string.status_save_failed_detail); return; }
                control.set(desired, (ok, state) -> {
                    if (isDestroyed()) return;
                    boolean applied = usable(ok, state) && desired.sameAs(ControlProtocol.readOptions(state));
                    setBusy(false);
                    if (applied) reload();
                    else message.setText(R.string.status_not_applied_detail);
                });
            });
        });
    }

    @Override protected void onDestroy() { worker.shutdown(); super.onDestroy(); }
}
