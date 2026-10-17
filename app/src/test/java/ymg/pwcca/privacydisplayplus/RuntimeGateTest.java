package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class RuntimeGateTest {
    @Test public void independentSettingsArePublishedTogetherBeforeTraversal() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        ProtectionOptions onlyFloating = new ProtectionOptions(true, false);
        gate.apply(onlyFloating, () -> {
            assertTrue(gate.options().sameAs(onlyFloating));
            assertTrue(gate.shouldRoute());
        });
        ProtectionOptions onlySplit = new ProtectionOptions(false, true);
        gate.apply(onlySplit, () -> {
            assertTrue(gate.options().sameAs(onlySplit));
            assertTrue(gate.shouldRoute());
        });
        assertTrue(gate.options().sameAs(onlySplit));
    }

    @Test public void failedRefreshRestoresBothIndependentChoices() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        ProtectionOptions previous = new ProtectionOptions(true, false);
        gate.apply(previous, () -> {});
        try {
            gate.apply(new ProtectionOptions(false, true), () -> { throw new IllegalAccessException(); });
            fail("Expected refresh failure");
        } catch (IllegalAccessException expected) { }
        assertTrue(gate.options().sameAs(previous));
        assertTrue(gate.isReady());
    }

    @Test public void startupRetainsNativeBehaviorUntilConfigurationArrives() {
        RuntimeGate gate = new RuntimeGate();
        assertFalse(gate.isReady());
        assertFalse(gate.shouldRoute());
        assertTrue(gate.isHealthy());
    }

    @Test public void savedOffConfigurationDoesNotMomentarilyEnableRouting() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        gate.apply(ProtectionOptions.NONE, () -> assertFalse(gate.shouldRoute()));
        assertTrue(gate.isReady());
        assertFalse(gate.shouldRoute());
    }

    @Test public void liveToggleIsVisibleBeforeTraversalIsRequested() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        gate.apply(ProtectionOptions.BOTH, () -> assertTrue(gate.shouldRoute()));
        gate.apply(ProtectionOptions.NONE, () -> assertFalse(gate.shouldRoute()));
        assertTrue(gate.options().sameAs(ProtectionOptions.NONE));
    }

    @Test public void failedLiveRefreshRestoresPreviousEnabledState() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        gate.apply(ProtectionOptions.BOTH, () -> {});
        try {
            gate.apply(ProtectionOptions.NONE, () -> { throw new IllegalAccessException(); });
            fail("Expected refresh failure");
        } catch (IllegalAccessException expected) { }
        assertTrue(gate.shouldRoute());
    }

    @Test public void failedFirstRefreshRestoresUninitializedState() {
        RuntimeGate gate = new RuntimeGate();
        try {
            gate.apply(ProtectionOptions.BOTH, () -> { throw new IllegalStateException(); });
            fail("Expected refresh failure");
        } catch (Exception expected) { }
        assertFalse(gate.isReady());
        assertFalse(gate.shouldRoute());
    }

    @Test public void toggleCannotReviveAHealthFailure() throws Exception {
        RuntimeGate gate = new RuntimeGate();
        gate.apply(ProtectionOptions.BOTH, () -> {});
        gate.disableOnFailure();
        assertFalse(gate.shouldRoute());
        try {
            gate.apply(ProtectionOptions.BOTH, () -> fail("Should not request traversal"));
            fail("Expected failed hook rejection");
        } catch (IllegalStateException expected) { }
        assertFalse(gate.isHealthy());
    }
}
