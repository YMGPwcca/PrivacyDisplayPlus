package ymg.pwcca.privacydisplayplus;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FloatingDragSessionTest {
    private final Object taskSurface = new Object(), buffer = new Object(), transaction = new Object();
    private final SplitPrivacyRegion.Bounds task = bounds(200, 400, 1000, 1600);
    private final SplitPrivacyRegion.Bounds display = bounds(0, 0, 1440, 3120);

    private FloatingDragSession session(SplitPrivacyRegion.Bounds window) {
        return new FloatingDragSession(taskSurface, task,
                List.of(new FloatingDragSession.Target(buffer, window, display)));
    }

    @Test public void edgeMovementUpdatesEveryTransactionWithoutAnyLayoutChangeOrFingerRelease() throws Exception {
        FloatingDragSession session = session(task);
        Writer writer = new Writer();
        for (int y = 500; y <= 900; y += 100) {
            Object currentTransaction = new Object();
            session.move(currentTransaction, taskSurface, -200, y, 1, writer);
            assertSame(currentTransaction, writer.transaction);
            assertArrayEquals(new float[]{1, 14, y, 600, y + 1200, 0, 0, 0, 0}, writer.region.data[0], 0);
        }
        assertEquals(5, writer.calls);
    }

    @Test public void movingFurtherOutAndBackToCenterRecomputesRatherThanTranslatesAnOldClip() throws Exception {
        FloatingDragSession session = session(task);
        Writer writer = new Writer();
        session.move(transaction, taskSurface, -200, 400, 1, writer);
        assertBounds(writer, 14, 400, 600, 1600);
        session.move(transaction, taskSurface, -500, 600, 1, writer);
        assertBounds(writer, 14, 600, 300, 1800);
        session.move(transaction, taskSurface, 300, 500, 1, writer);
        assertBounds(writer, 300, 500, 1100, 1700);
    }

    @Test public void rightTopAndBottomEdgesKeepMovingAlongThePanel() throws Exception {
        FloatingDragSession session = session(task);
        Writer writer = new Writer();
        session.move(transaction, taskSurface, 1100, 500, 1, writer);
        assertBounds(writer, 1100, 500, 1426, 1700);
        session.move(transaction, taskSurface, 100, -200, 1, writer);
        assertBounds(writer, 100, 16, 900, 1000);
        session.move(transaction, taskSurface, 300, 2800, 1, writer);
        assertBounds(writer, 300, 2800, 1100, 3104);
    }

    @Test public void contentInsetsAndNativeStashScaleAreAppliedBeforePanelClipping() throws Exception {
        FloatingDragSession session = session(bounds(210, 450, 990, 1590));
        Writer writer = new Writer();
        session.move(transaction, taskSurface, -100, 600, 0.5f, writer);
        assertBounds(writer, 14, 625, 295, 1195);
    }

    @Test public void fractionalPositionsRoundInwardsForVendorBounds() throws Exception {
        Writer writer = new Writer();
        session(task).move(transaction, taskSurface, 200.25f, 500.75f, 1, writer);
        assertBounds(writer, 201, 501, 1000, 1700);
    }

    @Test public void movingCompletelyOffscreenClearsRegionAndReturningRestoresIt() throws Exception {
        FloatingDragSession session = session(task);
        Writer writer = new Writer();
        session.move(transaction, taskSurface, -900, 400, 1, writer);
        assertNull(writer.region);
        session.move(transaction, taskSurface, -200, 500, 1, writer);
        assertBounds(writer, 14, 500, 600, 1700);
    }

    @Test public void unrelatedTransactionsClosedSessionsInvalidSurfacesAndBadCoordinatesAreIgnored() throws Exception {
        FloatingDragSession session = session(task);
        Writer writer = new Writer();
        session.move(transaction, new Object(), 0, 500, 1, writer);
        session.move(transaction, taskSurface, Float.NaN, 500, 1, writer);
        session.move(transaction, taskSurface, 0, Float.POSITIVE_INFINITY, 1, writer);
        session.move(transaction, taskSurface, 0, 500, 0, writer);
        session.move(transaction, taskSurface, 0, 500, 2, writer);
        writer.valid = false;
        session.move(transaction, taskSurface, 0, 500, 1, writer);
        writer.valid = true;
        session.close();
        session.move(transaction, taskSurface, 0, 500, 1, writer);
        assertEquals(0, writer.calls);
    }

    @Test public void multipleBuffersOfOneTaskShareTheNativeMovementTransaction() throws Exception {
        Object second = new Object();
        FloatingDragSession session = new FloatingDragSession(taskSurface, task, List.of(
                new FloatingDragSession.Target(buffer, task, display),
                new FloatingDragSession.Target(second, bounds(300, 500, 600, 800), display)));
        Writer writer = new Writer();
        session.move(transaction, taskSurface, -100, 600, 1, writer);
        assertEquals(List.of(buffer, second), writer.surfaces);
        assertSame(transaction, writer.transaction);
        assertBounds(writer, 14, 700, 300, 1000);
    }

    private static SplitPrivacyRegion.Bounds bounds(int l, int t, int r, int b) {
        return new SplitPrivacyRegion.Bounds(l, t, r, b);
    }
    private static void assertBounds(Writer writer, int l, int t, int r, int b) {
        assertArrayEquals(new float[]{1, l, t, r, b, 0, 0, 0, 0}, writer.region.data[0], 0);
    }
    private static final class Writer implements FloatingDragSession.Writer {
        boolean valid = true;
        int calls;
        Object transaction;
        SplitPrivacyRegion region;
        List<Object> surfaces = new ArrayList<>();
        @Override public boolean valid(Object surface) { return valid; }
        @Override public void queue(Object transaction, Object surface, SplitPrivacyRegion region) {
            calls++;
            this.transaction = transaction;
            this.region = region;
            surfaces.add(surface);
        }
    }
}
