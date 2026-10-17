package ymg.pwcca.privacydisplayplus;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.ImageButton;

/** A filled active state; the inactive state restores Samsung's original styling. */
final class HandleToggleStyle {
    private final ImageButton button;
    private final Drawable normalBackground, activeBackground;
    private final ColorStateList normalTint, activeTint;

    HandleToggleStyle(ImageButton button) {
        this.button = button;
        normalBackground = button.getBackground();
        normalTint = button.getImageTintList();
        int accent = accent(button.getContext()) | 0xff000000;
        GradientDrawable fill = circle(accent);
        int foreground = Color.luminance(accent) > 0.179f ? Color.BLACK : Color.WHITE;
        activeBackground = inset(new RippleDrawable(highlight(button.getContext(), foreground), fill, circle(Color.WHITE)));
        activeTint = ColorStateList.valueOf(foreground);
    }

    void apply(boolean on) {
        int left = button.getPaddingLeft(), top = button.getPaddingTop();
        int right = button.getPaddingRight(), bottom = button.getPaddingBottom();
        button.setBackground(on ? activeBackground : normalBackground);
        // Background padding must not resize the icon or the native touch target.
        button.setPadding(left, top, right, bottom);
        button.setImageTintList(on ? activeTint : normalTint);
    }

    static Drawable icon(Context context, int resourceId) {
        Drawable source = context.getDrawable(resourceId);
        if (!(source instanceof BitmapDrawable bitmapDrawable)) return source;
        Bitmap bitmap = bitmapDrawable.getBitmap();
        // Both inspected Samsung frames use a 256x256 canvas; the phone/shield
        // occupies (79,60)-(177,196). Fit the artwork rather than its empty canvas.
        Bitmap artwork = Bitmap.createBitmap(bitmap, bitmap.getWidth() * 79 / 256,
                bitmap.getHeight() * 60 / 256, bitmap.getWidth() * 98 / 256,
                bitmap.getHeight() * 136 / 256);
        return new BitmapDrawable(context.getResources(), artwork);
    }

    private Drawable inset(Drawable content) {
        if (normalBackground instanceof RippleDrawable nativeRipple && nativeRipple.getNumberOfLayers() > 0) {
            return new InsetDrawable(content, nativeRipple.getLayerInsetLeft(0), nativeRipple.getLayerInsetTop(0),
                    nativeRipple.getLayerInsetRight(0), nativeRipple.getLayerInsetBottom(0));
        }
        int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                button.getResources().getDisplayMetrics()));
        return new InsetDrawable(content, padding);
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable result = new GradientDrawable();
        result.setShape(GradientDrawable.OVAL);
        result.setColor(color);
        return result;
    }

    private static int accent(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)) {
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
        }
        return context.getColor(android.R.color.system_accent1_600);
    }

    private static ColorStateList highlight(Context context, int foreground) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorControlHighlight, value, true)) {
            if (value.resourceId != 0) return context.getColorStateList(value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return ColorStateList.valueOf(value.data);
            }
        }
        return ColorStateList.valueOf((foreground & 0xffffff) | 0x33000000);
    }
}
