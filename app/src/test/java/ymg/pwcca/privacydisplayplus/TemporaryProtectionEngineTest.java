package ymg.pwcca.privacydisplayplus;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static ymg.pwcca.privacydisplayplus.PopupRoutingEngineTest.*;

public class TemporaryProtectionEngineTest {
    private FakeAccess access;
    private Window window;
    private TemporaryProtectionEngine engine;
    private TestConditions conditions;

    @Before public void setup() {
        access = new FakeAccess();
        window = new Window();
        conditions = new TestConditions();
        engine = new TemporaryProtectionEngine(access, new WindowSurfacePrivacy(access), conditions);
    }

    @Test public void unselectedFloatingTaskReceivesPrivacyWithoutChangingSamsungAppPolicy() throws Exception {
        access.policy = 2; // Independent secured-screen/full-display request.
        engine.contribute(window);
        assertTrue(access.partial.contains(window.task));
        assertTrue(access.surfacePrivacy.get(window.buffer));
        assertEquals(2, access.policy);
        assertTrue(access.requests.isEmpty());
        assertEquals("previous", access.packageName);
    }

    @Test public void temporaryPopupUsesBoundedRegionAtPanelEdge() throws Exception {
        window.bounds = new SplitPrivacyRegion.Bounds(1000, 400, 1800, 1600);
        engine.contribute(window);
        assertArrayEquals(new float[]{1, 1000, 400, 1426, 1600, 0, 0, 0, 0},
                access.regions.get(window.buffer).data[0], 0);
        assertTrue(access.partial.contains(window.task));
    }

    @Test public void temporaryPopupWithoutUsableBufferOrRegionDoesNotClaimCoverage() throws Exception {
        window.buffer.valid = false;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
        window.buffer = null;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
        window.buffer = new Surface();
        access.regionAvailable = false;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void unselectedSplitTaskQueuesItsBufferRegionBeforeJoiningNativePartialSet() throws Exception {
        window.task.mode = 6;
        window.task.split = true;
        engine.contribute(window);
        assertTrue(access.partial.contains(window.task));
        assertTrue(access.surfacePrivacy.get(window.buffer));
        assertEquals(0, access.policy);
        assertTrue(access.requests.isEmpty());
    }

    @Test public void lackOfSelectionDrawabilityNativePermissionOrIntersectionAddsNothing() throws Exception {
        for (int denied = 0; denied < 4; denied++) {
            setup();
            conditions.values[denied] = false;
            engine.contribute(window);
            assertTrue(access.partial.isEmpty());
            assertTrue(access.surfacePrivacy.isEmpty());
            assertEquals(0, access.policy);
        }
    }

    @Test public void invalidOrMissingSplitBufferNeverClaimsPartialCoverage() throws Exception {
        window.task.mode = 6;
        window.task.split = true;
        window.buffer.valid = false;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
        window.buffer = null;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void rejectedSplitGeometryDoesNotAddNativeRequest() throws Exception {
        window.task.mode = 6;
        access.regionAvailable = false;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void failedRegionTransactionDoesNotAddRequestOrChangeFullPrivacy() throws Exception {
        window.task.mode = 6;
        access.policy = 2;
        access.failedSurfaceWrites = 1;
        try { engine.contribute(window); fail(); } catch (IllegalAccessException expected) { }
        assertTrue(access.partial.isEmpty());
        assertEquals(2, access.policy);
    }

    @Test public void stoppedSessionIsNotReaddedAfterSamsungRebuildsRequests() throws Exception {
        engine.contribute(window);
        access.partial.clear(); // Samsung's next beginPostLayoutPolicyLw.
        conditions.values[0] = false;
        engine.contribute(window);
        assertTrue(access.partial.isEmpty());
    }

    private static final class TestConditions implements TemporaryProtectionEngine.Conditions {
        final boolean[] values = {true, true, true, true};
        public boolean selected(Object task) { return values[0]; }
        public boolean drawable(Object window) { return values[1]; }
        public boolean allowed(Object controller) { return values[2]; }
        public boolean intersects(Object window, Object controller) { return values[3]; }
    }
}
