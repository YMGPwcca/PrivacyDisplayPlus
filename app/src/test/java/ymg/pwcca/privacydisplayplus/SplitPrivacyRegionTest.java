package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class SplitPrivacyRegionTest {
    private static final SplitPrivacyRegion.Bounds DISPLAY = bounds(0, 0, 1440, 3120);

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
