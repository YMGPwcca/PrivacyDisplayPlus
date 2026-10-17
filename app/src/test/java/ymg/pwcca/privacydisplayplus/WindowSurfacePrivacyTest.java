package ymg.pwcca.privacydisplayplus;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static ymg.pwcca.privacydisplayplus.PopupRoutingEngineTest.*;

public class WindowSurfacePrivacyTest {
    private FakeAccess access;
    private WindowSurfacePrivacy surfaces;
    private Window window;

    @Before public void setup() {
        access = new FakeAccess();
        surfaces = new WindowSurfacePrivacy(access);
        window = new Window();
        window.task.mode = 6;
        window.task.split = true;
    }

    @Test public void selectedSplitBufferRetainsPrivacyAfterNativeTaskFinalization() throws Exception {
        enable();
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertTrue(privacy(window.buffer));
    }

    @Test public void floatingChoiceDoesNotControlSplitBufferPrivacy() throws Exception {
        enable();
        surfaces.prepare(window, new ProtectionOptions(false, true));
        assertTrue(privacy(window.buffer));
    }

    @Test public void disablingSplitClearsItsBufferWhileFloatingRemainsOn() throws Exception {
        enable();
        surfaces.prepare(window, new ProtectionOptions(true, false));
        assertFalse(privacy(window.buffer));
    }

    @Test public void nativeTaskSuppressionOrSelectionRemovalClearsOwnedFlag() throws Exception {
        enable();
        window.task.requested = false;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
    }

    @Test public void leavingSplitForFullscreenOrPopupClearsOwnedFlag() throws Exception {
        for (int mode : new int[]{1, 5, 2}) {
            setup();
            enable();
            window.task.mode = mode;
            surfaces.prepare(window, ProtectionOptions.BOTH);
            assertFalse(privacy(window.buffer));
        }
    }

    @Test public void taskOrDisplayReplacementClearsOwnedFlag() throws Exception {
        enable();
        Surface previous = window.buffer;
        window.task = new Task();
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(previous));
        setup();
        enable();
        window.display = 1;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
    }

    @Test public void lostSplitStageOrLeafStatusClearsOwnedFlag() throws Exception {
        enable();
        window.task.split = false;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
        setup();
        enable();
        window.task.leaf = false;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
    }

    @Test public void surfaceReplacementClearsOldFlagWithoutWritingNewUnselectedBuffer() throws Exception {
        enable();
        Surface previous = window.buffer;
        window.buffer = new Surface();
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(previous));
        assertFalse(access.surfacePrivacy.containsKey(window.buffer));
    }

    @Test public void newSelectedSurfaceClearsOldSurfaceBeforeEnablingReplacement() throws Exception {
        enable();
        Surface previous = window.buffer;
        window.buffer = new Surface();
        enable();
        assertFalse(privacy(previous));
        assertTrue(privacy(window.buffer));
    }

    @Test public void failedCleanupRetainsOwnershipSoNextPrepareCanRetry() throws Exception {
        enable();
        access.failedSurfaceWrites = 1;
        try { surfaces.prepare(window, ProtectionOptions.NONE); fail("Expected cleanup failure"); }
        catch (IllegalAccessException expected) { }
        assertTrue(privacy(window.buffer));
        surfaces.prepare(window, ProtectionOptions.NONE);
        assertFalse(privacy(window.buffer));
    }

    @Test public void destroyedSurfaceIsForgottenWithoutAnInvalidTransaction() throws Exception {
        enable();
        window.buffer.valid = false;
        access.failedSurfaceWrites = 1;
        surfaces.prepare(window, ProtectionOptions.NONE);
        assertEquals(1, access.failedSurfaceWrites);
    }

    @Test public void unownedWindowDoesNotHaveNativePrivacyFlagsCleared() throws Exception {
        access.surfacePrivacy.put(window.buffer, true);
        surfaces.prepare(window, ProtectionOptions.NONE);
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(window.buffer)));
    }

    @Test public void separatePanesClearIndependently() throws Exception {
        enable();
        Window second = new Window();
        second.task.mode = 6;
        second.task.split = true;
        surfaces.enable(second, second.task, second.buffer);
        window.task.requested = false;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        surfaces.prepare(second, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
        assertTrue(privacy(second.buffer));
    }

    @Test public void repeatedUpdatesReuseTheSameBufferRegion() throws Exception {
        enable();
        enable();
        assertEquals(1, access.surfacePrivacy.size());
        assertTrue(privacy(window.buffer));
    }

    @Test public void missingGeometryCannotQueueAnUnboundedRegion() throws Exception {
        access.regionAvailable = false;
        assertFalse(surfaces.enable(window, window.task, window.buffer));
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void floatingRegionFollowsMovementResizeAndReturnFromPanelEdge() throws Exception {
        window.task.mode = 5;
        window.task.split = false;
        window.bounds = bounds(200, 400, 1000, 1600);
        enable();
        assertRegion(200, 400, 1000, 1600);
        window.bounds = bounds(-200, 400, 600, 1600);
        surfaces.prepare(window, new ProtectionOptions(true, false));
        assertRegion(14, 400, 600, 1600);
        window.bounds = bounds(900, 2600, 1500, 3300);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(900, 2600, 1426, 3104);
        window.bounds = bounds(300, 500, 900, 1200);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(300, 500, 900, 1200);
    }

    @Test public void floatingRegionFollowsRotationAndLandscapeEdges() throws Exception {
        window.task.mode = 5;
        enable();
        window.displayBounds = bounds(0, 0, 3120, 1440);
        window.bounds = bounds(2400, -20, 3200, 1000);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(2400, 16, 3106, 1000);
    }

    @Test public void floatingCleanupFollowsItsOwnSwitchAndNativeSuppression() throws Exception {
        window.task.mode = 5;
        enable();
        surfaces.prepare(window, new ProtectionOptions(true, false));
        assertTrue(privacy(window.buffer));
        surfaces.prepare(window, new ProtectionOptions(false, true));
        assertFalse(privacy(window.buffer));
        enable();
        window.task.requested = false;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
    }

    @Test public void floatingAppRuleUsesNativeSelectedPackageDuringCleanup() throws Exception {
        window.task.mode = 5;
        ProtectionOptions options = ProtectionOptions.NONE.withRule("example.bank", new AppRule(1, 2));
        assertTrue(surfaces.enable(window, window.task, window.buffer, "example.bank"));
        surfaces.prepare(window, options);
        assertTrue(privacy(window.buffer));
        surfaces.prepare(window, options.withRule("example.bank", new AppRule(2, 2)));
        assertFalse(privacy(window.buffer));
    }

    @Test public void floatingTransitionAndSurfaceReplacementClearOldCoverage() throws Exception {
        for (int mode : new int[]{1, 6}) {
            setup();
            window.task.mode = 5;
            enable();
            window.task.mode = mode;
            surfaces.prepare(window, ProtectionOptions.BOTH);
            assertFalse(privacy(window.buffer));
        }
        setup();
        window.task.mode = 5;
        enable();
        Surface old = window.buffer;
        window.buffer = new Surface();
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(old));
        assertFalse(access.surfacePrivacy.containsKey(window.buffer));
    }

    @Test public void movingCompletelyOutsideClearsStaleRegionAndAllowsFreshCoverageOnReturn() throws Exception {
        window.task.mode = 5;
        enable();
        window.bounds = bounds(-900, 400, -100, 1600);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
        window.bounds = bounds(200, 400, 1000, 1600);
        enable();
        assertRegion(200, 400, 1000, 1600);
    }

    @Test public void floatingRegionUpdateFailureCanBeClearedAfterDisablingHook() throws Exception {
        window.task.mode = 5;
        enable();
        window.bounds = bounds(100, 400, 900, 1600);
        access.failedSurfaceWrites = 1;
        try { surfaces.prepare(window, ProtectionOptions.BOTH); fail("Expected update failure"); }
        catch (IllegalAccessException expected) { }
        surfaces.prepare(window, ProtectionOptions.NONE);
        assertFalse(privacy(window.buffer));
    }

    @Test public void unchangedLayoutCannotOverwriteTheRegionsUpdatedByTheDragTransaction() throws Exception {
        window.task.mode = 5;
        window.bounds = bounds(200, 400, 1000, 1600);
        enable();
        surfaces.beginDrag(window.task, "first");
        SplitPrivacyRegion live = SplitPrivacyRegion.create(bounds(-200, 700, 600, 1900), window.displayBounds);
        access.queuePrivacyRegion(window, window.buffer, live); // SystemUI moves; WMS frame stays unchanged.
        int writes = access.surfaceWrites;
        enable(); // Another layout traversal sees the old frame.
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertEquals(writes, access.surfaceWrites);
        assertSame(live, access.regions.get(window.buffer));
        window.bounds = bounds(-200, 700, 600, 1900); // Layout catches up when the drag is committed.
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(14, 700, 600, 1900);
    }

    @Test public void endingCancelledDragRestoresLayoutEvenWhenItSnapsToItsOriginalBounds() throws Exception {
        window.task.mode = 5;
        window.bounds = bounds(200, 400, 1000, 1600);
        enable();
        surfaces.beginDrag(window.task, "first");
        access.queuePrivacyRegion(window, window.buffer,
                SplitPrivacyRegion.create(bounds(-200, 700, 600, 1900), window.displayBounds));
        surfaces.endDrag(window.task);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(200, 400, 1000, 1600);
    }

    @Test public void lostSystemUiSessionExpiresAndNativeFramesCanRestorePrivacy() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        surfaces = new WindowSurfacePrivacy(access, clock::get);
        window.task.mode = 5;
        window.bounds = bounds(200, 400, 1000, 1600);
        enable();
        surfaces.beginDrag(window.task, "first");
        access.queuePrivacyRegion(window, window.buffer,
                SplitPrivacyRegion.create(bounds(-200, 700, 600, 1900), window.displayBounds));
        int writes = access.surfaceWrites;
        clock.set(3500);
        surfaces.renewDrag(window.task, "first");
        clock.set(4500);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertEquals(writes, access.surfaceWrites);
        clock.set(6500);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(200, 400, 1000, 1600);
    }

    @Test public void lateFinishOrRenewalFromPreviousDragCannotChangeTheNewDragLease() throws Exception {
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(1000);
        surfaces = new WindowSurfacePrivacy(access, clock::get);
        window.task.mode = 5;
        enable();
        surfaces.beginDrag(window.task, "first");
        surfaces.beginDrag(window.task, "second");
        surfaces.endDrag(window.task, "first");
        int writes = access.surfaceWrites;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertEquals(writes, access.surfaceWrites);
        clock.set(3500);
        surfaces.renewDrag(window.task, "first");
        clock.set(4100);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertEquals(writes + 1, access.surfaceWrites);
    }

    @Test public void dragTargetsIncludeOnlyLiveProtectedBuffersOfTheChosenFloatingTask() throws Exception {
        window.task.mode = 5;
        enable();
        Window other = new Window();
        surfaces.enable(other, other.task, other.buffer);
        assertEquals(1, surfaces.dragTargets(window.task, ProtectionOptions.BOTH).size());
        assertSame(window.buffer, surfaces.dragTargets(window.task, ProtectionOptions.BOTH).get(0).surface());
        assertTrue(surfaces.dragTargets(window.task, ProtectionOptions.NONE).isEmpty());
        window.task.requested = false;
        assertTrue(surfaces.dragTargets(window.task, ProtectionOptions.BOTH).isEmpty());
    }

    @Test public void replacedAndSplitBuffersCannotBeExportedToAFloatingDrag() throws Exception {
        enable();
        assertTrue(surfaces.dragTargets(window.task, ProtectionOptions.BOTH).isEmpty());
        window.task.mode = 5;
        enable();
        window.buffer = new Surface();
        assertTrue(surfaces.dragTargets(window.task, ProtectionOptions.BOTH).isEmpty());
    }

    @Test public void failedChangedLayoutWriteIsRetriedAndDoesNotPoisonGeometryCache() throws Exception {
        window.task.mode = 5;
        enable();
        window.bounds = bounds(100, 400, 900, 1600);
        access.failedSurfaceWrites = 1;
        try { surfaces.prepare(window, ProtectionOptions.BOTH); fail("Expected write failure"); }
        catch (IllegalAccessException expected) { }
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertRegion(100, 400, 900, 1600);
    }

    @Test public void splitUpdatesContinueAndOwnershipChangesAlwaysQueueAFreshRegion() throws Exception {
        enable();
        int writes = access.surfaceWrites;
        surfaces.prepare(window, ProtectionOptions.BOTH);
        assertEquals(writes + 1, access.surfaceWrites);
        window.task.mode = 5;
        enable();
        writes = access.surfaceWrites;
        window.task = new Task();
        enable();
        assertEquals(writes + 1, access.surfaceWrites);
    }

    @Test public void newModeRegionOnSameBufferSurvivesOldModeCleanup() throws Exception {
        enable();
        window.task.mode = 5;
        window.task.split = false;
        enable();
        surfaces.prepare(window, new ProtectionOptions(true, false));
        assertTrue(privacy(window.buffer));
    }

    @Test public void floatingWindowsClearIndependentlyWhenOneMovesOffscreen() throws Exception {
        window.task.mode = 5;
        enable();
        Window other = new Window();
        surfaces.enable(other, other.task, other.buffer);
        window.bounds = bounds(-900, 400, -100, 1600);
        surfaces.prepare(window, ProtectionOptions.BOTH);
        surfaces.prepare(other, ProtectionOptions.BOTH);
        assertFalse(privacy(window.buffer));
        assertTrue(privacy(other.buffer));
    }

    private static SplitPrivacyRegion.Bounds bounds(int left, int top, int right, int bottom) {
        return new SplitPrivacyRegion.Bounds(left, top, right, bottom);
    }

    private void assertRegion(int left, int top, int right, int bottom) {
        assertArrayEquals(new float[]{1, left, top, right, bottom, 0, 0, 0, 0},
                access.regions.get(window.buffer).data[0], 0);
    }

    private void enable() throws Exception { surfaces.enable(window, window.task, window.buffer); }
    private boolean privacy(Surface surface) { return Boolean.TRUE.equals(access.surfacePrivacy.get(surface)); }
}
