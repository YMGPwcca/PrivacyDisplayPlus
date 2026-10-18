package ymg.pwcca.privacydisplayplus;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.PopupWindow;
import android.widget.TextView;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.ListPopupWindow;
import com.google.android.material.color.MaterialColors;
import java.util.function.IntConsumer;

/** An anchored, single-choice Material menu presented as a Settings row. */
final class SettingsChoiceMenu {
    private final View anchor, control, arrow;
    private final TextView value;
    private final String[] labels, valueLabels;
    private final IntConsumer onSelection;
    private ListPopupWindow popup;
    private int selected;

    SettingsChoiceMenu(View anchor, int title, int description, String[] labels, IntConsumer onSelection) {
        this(anchor, title, description, labels, labels, onSelection);
    }

    SettingsChoiceMenu(View anchor, int title, int description, String[] labels, String[] valueLabels,
                       IntConsumer onSelection) {
        this.anchor = anchor;
        this.labels = labels.clone();
        this.valueLabels = valueLabels.clone();
        this.onSelection = onSelection;
        control = anchor.findViewById(R.id.choice_control);
        value = anchor.findViewById(R.id.choice_value);
        arrow = anchor.findViewById(R.id.choice_arrow);
        var context = anchor.getContext();
        ((TextView) anchor.findViewById(R.id.choice_title)).setText(title);
        TextView detail = anchor.findViewById(R.id.choice_description);
        if (description == 0) detail.setVisibility(View.GONE);
        else detail.setText(description);
        anchor.setContentDescription(context.getString(title)
                + (description == 0 ? "" : ". " + context.getString(description)));
        anchor.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setCanOpenPopup(true);
            }
        });
        anchor.setOnClickListener(ignored -> show());
        anchor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {}
            @Override public void onViewDetachedFromWindow(View view) { dismiss(); }
        });
    }

    void setSelection(int index) {
        selected = index;
        value.setText(valueLabels[index]);
        anchor.setStateDescription(labels[index]);
    }

    void setEnabled(boolean enabled) {
        anchor.setEnabled(enabled);
        anchor.setAlpha(enabled ? 1f : .38f);
        if (!enabled) dismiss();
    }

    private void show() {
        if (!anchor.isEnabled() || !anchor.isAttachedToWindow()) return;
        dismiss();
        var context = anchor.getContext();
        ListPopupWindow menu = new ListPopupWindow(context);
        popup = menu;
        menu.setAnchorView(control);
        menu.setModal(true);
        menu.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        menu.setDropDownGravity(Gravity.END);
        Choices choices = new Choices();
        menu.setWidth(menuWidth(choices));
        menu.setVerticalOffset(dp(4));
        menu.setBackgroundDrawable(AppCompatResources.getDrawable(context, R.drawable.settings_menu_background));
        menu.setAdapter(choices);
        menu.setOnItemClickListener((parent, view, position, id) -> {
            setSelection(position);
            menu.dismiss();
            onSelection.accept(position);
        });
        menu.setOnDismissListener(() -> {
            arrow.setRotation(90);
            if (popup == menu) popup = null;
        });
        arrow.setRotation(270);
        menu.show();
    }

    private void dismiss() { if (popup != null) popup.dismiss(); }
    private int dp(int size) { return Math.round(size * anchor.getResources().getDisplayMetrics().density); }

    private int menuWidth(Choices choices) {
        int width = dp(160);
        for (int i = 0; i < choices.getCount(); i++) {
            CheckedTextView item = (CheckedTextView) choices.getView(i, null, (ViewGroup) anchor);
            int text = (int) Math.ceil(item.getPaint().measureText(labels[i]));
            width = Math.max(width, text + item.getCompoundPaddingLeft() + item.getCompoundPaddingRight() + dp(8));
        }
        int available = anchor.getRootView().getWidth() - dp(32);
        return Math.min(width, Math.min(dp(240), available));
    }

    private final class Choices extends BaseAdapter {
        @Override public int getCount() { return labels.length; }
        @Override public String getItem(int position) { return labels[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View recycled, ViewGroup parent) {
            CheckedTextView row = recycled == null
                    ? (CheckedTextView) LayoutInflater.from(parent.getContext()).inflate(R.layout.settings_menu_item, parent, false)
                    : (CheckedTextView) recycled;
            row.setText(labels[position]);
            row.setChecked(position == selected);
            Drawable check = AppCompatResources.getDrawable(row.getContext(), R.drawable.ic_menu_check).mutate();
            check.setTint(MaterialColors.getColor(row, com.google.android.material.R.attr.colorOnSecondaryContainer));
            check.setAlpha(position == selected ? 255 : 0);
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(check, null, null, null);
            return row;
        }
    }
}
