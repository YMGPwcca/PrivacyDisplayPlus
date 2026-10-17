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

    private SplitPrivacyRegion(int width, int height, int left, int top, int right, int bottom) {
        this.width = width;
        this.height = height;
        data = new float[][]{{1, left, top, right, bottom, 0, 0, 0, 0}};
    }

    static SplitPrivacyRegion create(Bounds window, Bounds display) {
        int left = Math.max(window.left, display.left + HORIZONTAL_GUARD);
        int top = Math.max(window.top, display.top + VERTICAL_GUARD);
        int right = Math.min(window.right, display.right - HORIZONTAL_GUARD);
        int bottom = Math.min(window.bottom, display.bottom - VERTICAL_GUARD);
        if (right <= left || bottom <= top) return null;
        return new SplitPrivacyRegion(window.right - window.left, window.bottom - window.top,
                left, top, right, bottom);
    }
}
