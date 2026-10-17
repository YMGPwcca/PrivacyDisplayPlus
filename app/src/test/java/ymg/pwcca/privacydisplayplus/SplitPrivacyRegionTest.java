package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class SplitPrivacyRegionTest {
    private static final SplitPrivacyRegion.Bounds DISPLAY = bounds(0, 0, 1440, 3120);

    @Test public void floatingCornersKeepTheirNativeRadiusAwayFromPanelEdges() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(200, 400, 1000, 1600), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 200, 400, 1000, 1600, 90, 90, 90, 90}, r.data[0], 0);
    }

    @Test public void horizontalPanelCutUsesTheVendorsSingleSquareRadius() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(-200, 400, 600, 1600), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 14, 400, 600, 1600, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void panelGuardReducesOnlyCornersTouchedByTheGuard() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(5, 10, 1000, 1600), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 14, 16, 1000, 1600, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void bottomRightClipPreservesTopLeftAndAdjustsOtherCornersSeparately() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(900, 2600, 1450, 3130), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 900, 2600, 1426, 3104, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void evenOnePixelOfHorizontalClippingMakesBothCutCornersSquare() {
        SplitPrivacyRegion left = SplitPrivacyRegion.create(bounds(13, 400, 813, 1600), DISPLAY, 90);
        SplitPrivacyRegion right = SplitPrivacyRegion.create(bounds(627, 400, 1427, 1600), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 14, 400, 813, 1600, 0, 0, 0, 0}, left.data[0], 0);
        assertArrayEquals(new float[]{1, 627, 400, 1426, 1600, 0, 0, 0, 0}, right.data[0], 0);
    }

    @Test public void touchingEitherGuardExactlyIsSquareAndMovingOnePixelInRestoresRounding() {
        SplitPrivacyRegion left = SplitPrivacyRegion.create(bounds(14, 400, 814, 1600), DISPLAY, 90);
        SplitPrivacyRegion right = SplitPrivacyRegion.create(bounds(626, 400, 1426, 1600), DISPLAY, 90);
        assertEquals(0, left.data[0][5], 0);
        assertEquals(0, left.data[0][7], 0);
        assertEquals(0, right.data[0][6], 0);
        assertEquals(0, right.data[0][8], 0);
        SplitPrivacyRegion inside = SplitPrivacyRegion.create(bounds(625, 400, 1425, 1600), DISPLAY, 90);
        assertEquals(90, inside.data[0][6], 0);
        assertEquals(90, inside.data[0][8], 0);
    }

    @Test public void cornersCannotExceedHalfTheClippedWindowSize() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(200, 400, 240, 600), DISPLAY, 90);
        assertArrayEquals(new float[]{1, 200, 400, 240, 600, 20, 20, 20, 20}, r.data[0], 0);
    }

    @Test public void invalidRadiusCannotProduceMalformedNativeMetadata() {
        for (float radius : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertNull(SplitPrivacyRegion.create(bounds(200, 400, 1000, 1600), DISPLAY, radius));
        }
    }

    @Test public void bottomPaneKeepsExpandedPanelBoundsInsideDisplay() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(0, 1575, 1440, 3120), DISPLAY);
        assertNotNull(r);
        assertArrayEquals(new float[]{1, 14, 1575, 1426, 3104, 0, 0, 0, 0}, r.data[0], 0);
        assertEquals(1440, r.width);
        assertEquals(1545, r.height);
        assertTrue(r.data[0][1] - 1 - 12 >= 1);
        assertTrue(r.data[0][3] + 2 + 12 <= 1440);
        assertTrue(r.data[0][4] + 2 + 12 <= 3120);
    }

    @Test public void topPaneAddsGuardOnlyAtPanelEdgesAndKeepsDividerBoundary() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(0, 0, 1440, 1545), DISPLAY);
        assertArrayEquals(new float[]{1, 14, 16, 1426, 1545, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void horizontalEdgesSaturateExactVendorMinimum() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(0, 0, 1440, 1545), DISPLAY);
        assertEquals(1, r.data[0][1] - 1 - 12, 0);
        assertEquals(1440, r.data[0][3] + 2 + 12, 0);
    }

    @Test public void reducingEitherHorizontalInsetByOneFailsVendorBounds() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(0, 0, 1440, 1545), DISPLAY);
        assertTrue((r.data[0][1] - 1) - 1 - 12 < 1);
        assertTrue((r.data[0][3] + 1) + 2 + 12 > 1440);
    }

    @Test public void paneInsideDisplayKeepsAllItsBounds() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(100, 200, 1200, 1500), DISPLAY);
        assertArrayEquals(new float[]{1, 100, 200, 1200, 1500, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void nearPanelEdgesClampToMinimumInsteadOfAddingAnotherInset() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(5, 20, 1435, 1500), DISPLAY);
        assertArrayEquals(new float[]{1, 14, 20, 1426, 1500, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void landscapeRightPaneUsesLandscapePanelEdges() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(1575, 0, 3120, 1440), bounds(0, 0, 3120, 1440));
        assertArrayEquals(new float[]{1, 1575, 16, 3106, 1424, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void partlyOutsideWindowIsClippedBeforeGuarding() {
        SplitPrivacyRegion r = SplitPrivacyRegion.create(bounds(-10, 1575, 1450, 3130), DISPLAY);
        assertArrayEquals(new float[]{1, 14, 1575, 1426, 3104, 0, 0, 0, 0}, r.data[0], 0);
    }

    @Test public void emptyReversedOutsideOrTooNarrowRegionIsRejected() {
        for (SplitPrivacyRegion.Bounds bad : new SplitPrivacyRegion.Bounds[]{
                bounds(0, 0, 0, 100), bounds(10, 0, 0, 100),
                bounds(1500, 0, 1600, 100), bounds(0, 0, 3, 100)}) {
            assertNull(SplitPrivacyRegion.create(bad, DISPLAY));
        }
    }

    private static SplitPrivacyRegion.Bounds bounds(int l, int t, int r, int b) {
        return new SplitPrivacyRegion.Bounds(l, t, r, b);
    }
}
