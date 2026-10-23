package ymg.pwcca.privacydisplayplus;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static ymg.pwcca.privacydisplayplus.PopupRoutingEngineTest.*;

public class NotificationShadePrivacyTest {
    private FakeAccess access;
    private RuntimeGate gate;
    private NotificationShadePrivacy panels;

    @Before public void setup() throws Exception {
        access = new FakeAccess();
        gate = new RuntimeGate();
        gate.apply(ProtectionOptions.NONE.withPanelPrivacy(true, true, true, true), () -> {});
        panels = new NotificationShadePrivacy(access, gate);
    }

    @Test public void notificationAndQuickSettingsAreDetectedIndependentlyAndAtTransitions() {
        assertTrue(NotificationShadePrivacy.notificationCenterOpen(0.3f, true, 1, 0, false, false));
        assertFalse(NotificationShadePrivacy.quickSettingsOpen(0.3f, true, 1, 0, false, false));
        assertFalse(NotificationShadePrivacy.notificationCenterOpen(1, true, 0, 0, false, false));
        assertTrue(NotificationShadePrivacy.quickSettingsOpen(1, true, 0, 0, false, false));
        for (int state : new int[]{2, 3}) {
            assertTrue(NotificationShadePrivacy.notificationCenterOpen(0.5f, true, state, 0, false, false));
            assertTrue(NotificationShadePrivacy.quickSettingsOpen(0.5f, true, state, 0, false, false));
        }
    }

    @Test public void headsUpClosedShadeDozeBouncerAndUnrecognizedStatesDoNotOpenPanels() {
        assertFalse(NotificationShadePrivacy.notificationCenterOpen(0, true, 1, 0, false, false));
        assertFalse(NotificationShadePrivacy.quickSettingsOpen(0, true, 0, 0, false, false));
        assertFalse(NotificationShadePrivacy.notificationCenterOpen(1, false, 1, 0, false, false));
        assertFalse(NotificationShadePrivacy.quickSettingsOpen(1, true, 0, 1, false, false));
        assertFalse(NotificationShadePrivacy.notificationCenterOpen(1, true, 1, 2, true, false));
        assertFalse(NotificationShadePrivacy.quickSettingsOpen(1, true, 2, 2, false, true));
        assertFalse(NotificationShadePrivacy.notificationCenterOpen(1, true, -1, 0, false, false));
        assertFalse(NotificationShadePrivacy.quickSettingsOpen(1, true, 4, 0, false, false));
    }

    @Test public void notificationCenterOnlyDoesNotProtectQuickSettings() throws Exception {
        gate.apply(ProtectionOptions.NONE.withPanelPrivacy(true, false, false, false), () -> {});
        panels.contribute(access, true, false, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);

        beginNativeLayout(0, "previous");
        panels.contribute(access, false, true, 0, 0);
        assertEquals(0, access.policy);
        assertTrue(access.requests.isEmpty());
    }

    @Test public void quickSettingsOnlyDoesNotProtectNotificationCenter() throws Exception {
        gate.apply(ProtectionOptions.NONE.withPanelPrivacy(false, true, false, false), () -> {});
        panels.contribute(access, true, false, 0, 0);
        assertEquals(0, access.policy);
        assertTrue(access.requests.isEmpty());
        panels.contribute(access, false, true, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);
    }

    @Test public void transitionsProtectBothOnlyWhenBothPanelSwitchesAreEnabled() throws Exception {
        panels.contribute(access, true, true, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);
        gate.apply(ProtectionOptions.NONE.withPanelPrivacy(false, true, false, false), () -> {});
        beginNativeLayout(0, "previous");
        panels.contribute(access, true, true, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);
    }

    @Test public void eachMaximumChoiceRequiresItsOwnOpenAndEnabledPanel() {
        ProtectionOptions options = ProtectionOptions.NONE.withPanelPrivacy(true, true, true, false);
        assertTrue(NotificationShadePrivacy.maximumRequested(true, false, options));
        assertFalse(NotificationShadePrivacy.maximumRequested(false, true, options));
        options = options.withPanelPrivacy(true, true, true, true);
        assertTrue(NotificationShadePrivacy.maximumRequested(false, true, options));
        assertTrue(NotificationShadePrivacy.maximumRequested(true, false, options));
        assertTrue(NotificationShadePrivacy.maximumRequested(true, true, options));
        options = options.withPanelPrivacy(false, false, true, true);
        assertFalse(NotificationShadePrivacy.maximumRequested(true, false, options));
        assertFalse(NotificationShadePrivacy.maximumRequested(false, true, options));
    }

    @Test public void lockScreenStopsAtOcclusionDozeBouncerOrUnlock() {
        assertTrue(NotificationShadePrivacy.lockScreenOpen(true, false, false, false, false, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(false, false, false, false, false, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(true, true, false, false, false, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(true, false, true, false, false, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(true, false, false, true, false, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(true, false, false, false, true, false));
        assertFalse(NotificationShadePrivacy.lockScreenOpen(true, false, false, false, false, true));
    }

    @Test public void lockScreenRequestAndMaximumAreIndependentOfPanels() throws Exception {
        ProtectionOptions lock = ProtectionOptions.NONE.withLockScreenPrivacy(true, true);
        gate.apply(lock, () -> {});
        assertTrue(NotificationShadePrivacy.maximumRequested(false, false, true, lock));
        assertFalse(NotificationShadePrivacy.maximumRequested(false, false, false, lock));
        panels.contribute(access, false, false, true, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);
        beginNativeLayout(0, "previous");
        panels.contribute(access, false, false, false, 0, 0);
        assertEquals(0, access.policy);
        assertTrue(access.requests.isEmpty());
    }

    @Test public void maximumAloneDoesNotAddProtectionRequest() throws Exception {
        gate.apply(ProtectionOptions.NONE.withPanelPrivacy(false, false, true, true), () -> {});
        assertFalse(NotificationShadePrivacy.maximumRequested(true, true, gate.options()));
        panels.contribute(access, true, true, 0, 0);
        assertEquals(0, access.policy);
        assertTrue(access.requests.isEmpty());
    }

    @Test public void openingPanelsAddsOnlyFullScreenReasonWithoutRegionalTasks() throws Exception {
        panels.contribute(access, true, false, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(NotificationShadePrivacy.SOURCE, access.packageName);
        assertEquals(List.of(NotificationShadePrivacy.SOURCE), access.requests);
        assertTrue(access.partial.isEmpty());
        assertEquals(0, access.surfaceWrites);
    }

    @Test public void closingPanelReleasesOnlyItsContributionOnNextNativeLayout() throws Exception {
        panels.contribute(access, true, false, 0, 0);
        beginNativeLayout(2, "example.secured");
        panels.contribute(access, false, false, 0, 0);
        assertEquals(2, access.policy);
        assertEquals("example.secured", access.packageName);
        assertEquals(List.of("example.secured"), access.requests);
        panels.contribute(access, false, true, 0, 0);
        assertEquals(3, access.policy);
        assertEquals("example.secured", access.packageName);
        assertEquals(List.of("example.secured", NotificationShadePrivacy.SOURCE), access.requests);
    }

    @Test public void nativeAppAndPartialRequestsSurvivePanelTransitions() throws Exception {
        Object task = new Object();
        beginNativeLayout(1, "example.bank");
        access.partial.add(task);
        panels.contribute(access, true, true, 0, 0);
        assertEquals(List.of("example.bank", NotificationShadePrivacy.SOURCE), access.requests);
        assertEquals("example.bank", access.packageName);
        assertTrue(access.partial.contains(task));
        beginNativeLayout(1, "example.bank");
        panels.contribute(access, false, false, 0, 0);
        assertEquals(1, access.policy);
        assertEquals(List.of("example.bank"), access.requests);
        assertTrue(access.partial.contains(task));
    }

    @Test public void unsupportedDisplaysUsersModesAndNativeReasonsAreUntouched() throws Exception {
        for (int denied = 0; denied < 6; denied++) {
            setup();
            if (denied == 3) access.mode = 0;
            if (denied == 4) access.mode = 1;
            if (denied == 5) access.policy = 4;
            panels.contribute(access, denied != 0, denied != 0, denied == 1 ? 1 : 0, denied == 2 ? 10 : 0);
            assertEquals(denied == 5 ? 4 : 0, access.policy);
            assertEquals("previous", access.packageName);
            assertTrue(access.requests.isEmpty());
        }
    }

    @Test public void unavailableOrFailedHookCannotAddPanelRequest() throws Exception {
        for (boolean failed : new boolean[]{false, true}) {
            gate = new RuntimeGate();
            if (failed) {
                gate.apply(ProtectionOptions.NONE.withPanelPrivacy(true, true, true, true), () -> {});
                gate.disableOnFailure();
            }
            new NotificationShadePrivacy(access, gate).contribute(access, true, true, 0, 0);
            assertEquals(0, access.policy);
            assertTrue(access.requests.isEmpty());
        }
    }

    @Test public void failedPanelContributionRestoresNativeRequestAndPackage() throws Exception {
        beginNativeLayout(2, "example.secured");
        access.failedPolicyWrites = 1;
        try {
            panels.contribute(access, true, false, 0, 0);
            fail("Expected reflective write failure");
        } catch (IllegalAccessException expected) { }
        assertEquals(2, access.policy);
        assertEquals("example.secured", access.packageName);
        assertEquals(List.of("example.secured"), access.requests);
    }

    private void beginNativeLayout(int policy, String packageName) {
        access.policy = policy;
        access.packageName = packageName;
        access.requests.clear();
        if (policy != 0) access.requests.add(packageName);
    }
}
