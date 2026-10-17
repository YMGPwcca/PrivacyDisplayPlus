package ymg.pwcca.privacydisplayplus;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static ymg.pwcca.privacydisplayplus.PopupRoutingEngineTest.*;

public class SplitSurfacePrivacyTest {
    private FakeAccess access;
    private SplitSurfacePrivacy surfaces;
    private Window window;

    @Before public void setup() {
        access = new FakeAccess();
        surfaces = new SplitSurfacePrivacy(access);
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

    private void enable() throws Exception { surfaces.enable(window, window.task, window.buffer); }
    private boolean privacy(Surface surface) { return Boolean.TRUE.equals(access.surfacePrivacy.get(surface)); }
}
