package ymg.pwcca.privacydisplayplus;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import com.google.android.material.color.DynamicColors;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.divider.MaterialDivider;

/** Shared compact Material list shell; each page owns its control requests. */
abstract class FeatureListActivity extends AppCompatActivity {
    ControlClient control;
    LinearLayout rows;
    TextView message;
    boolean busy;
    abstract int pageTitle();
    abstract int pageIntro();
    abstract void reload();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        DynamicColors.applyToActivityIfAvailable(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        var insetsController = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(light);
        insetsController.setAppearanceLightNavigationBars(light);
        setContentView(R.layout.activity_feature_list);
        control = new ControlClient(this);
        rows = findViewById(R.id.feature_rows);
        message = findViewById(R.id.feature_status);
        MaterialToolbar toolbar = findViewById(R.id.feature_toolbar);
        toolbar.setTitle(pageTitle());
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription(R.string.back);
        toolbar.setNavigationOnClickListener(view -> finish());
        ((TextView) findViewById(R.id.feature_intro)).setText(pageIntro());
        findViewById(R.id.feature_refresh).setOnClickListener(view -> reload());
        findViewById(R.id.feature_screen).setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override protected void onResume() { super.onResume(); if (!busy) reload(); }
    @Override protected void onDestroy() { control.close(); super.onDestroy(); }

    void loading() {
        setBusy(true);
        message.setText(R.string.status_connecting);
    }

    void setBusy(boolean value) {
        busy = value;
        findViewById(R.id.feature_refresh).setEnabled(!value);
        for (int i = 0; i < rows.getChildCount(); i++) rows.getChildAt(i).setEnabled(!value);
    }

    boolean usable(boolean acknowledged, Bundle state) {
        if (isDestroyed()) return false;
        return acknowledged && ControlProtocol.hasOptions(state)
                && state.getInt(ControlProtocol.VERSION) == ControlProtocol.VERSION_CODE
                && state.getBoolean(ControlProtocol.READY) && state.getBoolean(ControlProtocol.HEALTHY);
    }

    void clearRows() { rows.removeAllViews(); findViewById(R.id.feature_card).setVisibility(View.GONE); }

    void addRow(String title, String detail, Runnable action) {
        if (rows.getChildCount() != 0) {
            MaterialDivider divider = new MaterialDivider(this);
            divider.setDividerInsetStart(dp(16));
            divider.setDividerInsetEnd(dp(16));
            rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        View row = getLayoutInflater().inflate(R.layout.settings_action_row, rows, false);
        ((TextView) row.findViewById(R.id.action_title)).setText(title);
        ((TextView) row.findViewById(R.id.action_detail)).setText(detail);
        row.setContentDescription(title + ". " + detail);
        row.setOnClickListener(view -> { if (!busy) action.run(); });
        rows.addView(row);
        findViewById(R.id.feature_card).setVisibility(View.VISIBLE);
    }

    String label(String name) {
        try {
            var app = getPackageManager().getApplicationInfo(name, 0);
            return getPackageManager().getApplicationLabel(app).toString();
        } catch (android.content.pm.PackageManager.NameNotFoundException missing) { return name; }
    }

    int dp(int size) { return Math.round(getResources().getDisplayMetrics().density * size); }
}
