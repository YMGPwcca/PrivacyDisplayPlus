package ymg.pwcca.privacydisplayplus;

/** Native region data uses display coordinates and four corner radii. */
final class SplitPrivacyRegion {
    // Exact firmware: vendor DimmingWidth=12, offsets=-1/-1/+2/+2;
    // AreaModeBounds requires left/top - 12 >= 1 and right/bottom + 12 <= panel.
    // 14 is the exact horizontal minimum: 14 - 1 - 12 == 1;
    // panelWidth - 14 + 2 + 12 == panelWidth. A 13px inset fails both checks.
    private static final int HORIZONTAL_GUARD = 14;
    private static final int VERTICAL_GUARD = 16;
    record Bounds(int left, int top, int right, int bottom) {}
    final int width, height;
    final float[][] data;

    private SplitPrivacyRegion(Bounds window, Bounds display, int left, int top, int right, int bottom, float radius) {
        width = window.right - window.left;
        height = window.bottom - window.top;
        float limit = Math.min(right - left, bottom - top) / 2f;
        // This firmware's compositor exposes one radius for each privacy region.
        // Match its working left-edge behavior on BOTH sides; per-corner zeros
        // alone leave the right edge rounded because the native radius stays nonzero.
        boolean cut = left == display.left + HORIZONTAL_GUARD || right == display.right - HORIZONTAL_GUARD;
        float nativeRadius = cut ? 0 : radius;
        data = new float[][]{{1, left, top, right, bottom,
                corner(nativeRadius, left - window.left, top - window.top, limit),
                corner(nativeRadius, window.right - right, top - window.top, limit),
                corner(nativeRadius, left - window.left, window.bottom - bottom, limit),
                corner(nativeRadius, window.right - right, window.bottom - bottom, limit)}};
    }

    private static float corner(float radius, int horizontalClip, int verticalClip, float limit) {
        return Math.min(limit, Math.max(0, radius - Math.max(horizontalClip, verticalClip)));
    }

    static SplitPrivacyRegion create(Bounds window, Bounds display) {
        return create(window, display, 0);
    }

    static SplitPrivacyRegion create(Bounds window, Bounds display, float radius) {
        if (!Float.isFinite(radius) || radius < 0) return null;
        int left = Math.max(window.left, display.left + HORIZONTAL_GUARD);
        int top = Math.max(window.top, display.top + VERTICAL_GUARD);
        int right = Math.min(window.right, display.right - HORIZONTAL_GUARD);
        int bottom = Math.min(window.bottom, display.bottom - VERTICAL_GUARD);
        if (right <= left || bottom <= top) return null;
        return new SplitPrivacyRegion(window, display, left, top, right, bottom, radius);
    }
}
