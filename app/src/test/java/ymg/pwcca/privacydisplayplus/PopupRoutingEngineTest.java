package ymg.pwcca.privacydisplayplus;

import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class PopupRoutingEngineTest {
    private FakeAccess access;
    private PopupRoutingEngine engine;
    private WindowSurfacePrivacy windowSurfaces;
    private Window window;

    @Before public void setup() {
        access = new FakeAccess();
        windowSurfaces = new WindowSurfacePrivacy(access);
        engine = new PopupRoutingEngine(access, windowSurfaces);
        window = new Window();
    }

    @Test public void handleOffRemovesOnlyThisWindowsNativeAppContributionWithoutAddingPartial() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("example.bank");
        assertTrue(engine.suppressAppRequest(snapshot));
        assertEquals(0, access.policy);
        assertEquals("previous", access.packageName);
        assertEquals(List.of("example.bank"), access.requests);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void handleOffPreservesIndependentAppAndSecureFullScreenReasons() throws Exception {
        for (int prior : new int[]{1, 2, 3}) {
            setup();
            access.policy = prior;
            PopupRoutingEngine.Snapshot snapshot = before(window);
            samsungAppRequest("example.bank");
            assertTrue(engine.suppressAppRequest(snapshot));
            assertEquals(prior, access.policy);
        }
    }

    @Test public void handleOffNeverRewritesTheNativeSecuredWindowRequest() throws Exception {
        access.option = 256;
        window.samsungFlags = 4;
        PopupRoutingEngine.Snapshot snapshot = before(window);
        assertNull(snapshot);
        access.policy = 2;
        assertFalse(engine.suppressAppRequest(snapshot));
        assertEquals(2, access.policy);
    }

    @Test public void handleOffCannotRemoveAnUnobservedOrAmbiguousContribution() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        assertFalse(engine.suppressAppRequest(snapshot));
        samsungAppRequest("example.bank");
        access.requests.add("example.other");
        assertFalse(engine.suppressAppRequest(snapshot));
        assertEquals(1, access.policy);
    }

    @Test public void handleOffCannotAffectAChangedWindowOrFullscreenTask() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("example.bank");
        window.task.mode = 1;
        assertFalse(engine.suppressAppRequest(snapshot));
        assertEquals(1, access.policy);
    }

    @Test public void handleOffLeavesAnotherTasksPartialRequestIntact() throws Exception {
        Object otherTask = new Object();
        access.partial.add(otherTask);
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("example.bank");
        assertTrue(engine.suppressAppRequest(snapshot));
        assertEquals(Set.of(otherTask), access.partial);
    }

    @Test public void failedHandleOffRestoresFullPrivacyBeforeFailingTheHook() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("example.bank");
        String nativePackage = access.packageName;
        access.failedPolicyWrites = 1;
        try { engine.suppressAppRequest(snapshot); fail(); } catch (IllegalAccessException expected) { }
        assertEquals(1, access.policy);
        assertEquals(nativePackage, access.packageName);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void selectedPopupUsesTaskPrivacyAndKeepsSamsungRequestList() throws Exception {
        access.requireBufferBeforeRemovingApp = true;
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        assertTrue(after(s));
        assertEquals(0, access.policy);
        assertTrue(access.partial.contains(target(window)));
        assertEquals(List.of("popup"), access.requests);
        assertEquals("previous", access.packageName);
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(window.buffer)));
    }

    @Test public void popupCrossingPanelEdgeQueuesClippedRegionBeforeRemovingFullRequest() throws Exception {
        window.bounds = new SplitPrivacyRegion.Bounds(-200, 400, 600, 1600);
        access.requireBufferBeforeRemovingApp = true;
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("popup");
        assertTrue(after(snapshot));
        assertArrayEquals(new float[]{1, 14, 400, 600, 1600, 0, 0, 0, 0},
                access.regions.get(window.buffer).data[0], 0);
        assertEquals(Set.of(window.task), access.partial);
    }

    @Test public void popupWithoutUsableGeometryKeepsFullAppProtection() throws Exception {
        window.bounds = new SplitPrivacyRegion.Bounds(-800, 400, 10, 1600);
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("popup");
        assertFalse(after(snapshot));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void popupMissingOrInvalidBufferKeepsNativeRouting() throws Exception {
        window.buffer.valid = false;
        assertNull(before(window));
        window.buffer = null;
        assertNull(before(window));
    }

    @Test public void popupBufferReplacementDuringCallbackKeepsFullAppProtection() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("popup");
        window.buffer = new Surface();
        assertFalse(after(snapshot));
        assertEquals(1, access.policy);
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void failedPopupRegionWriteDoesNotRemoveFullAppProtection() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = before(window);
        samsungAppRequest("popup");
        access.failedSurfaceWrites = 1;
        try { after(snapshot); fail("Expected region transaction failure"); }
        catch (IllegalAccessException expected) { }
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void fullScreenRulePreservesTheSelectedAppsNativeContribution() throws Exception {
        ProtectionOptions options = ProtectionOptions.BOTH.withRule("example.bank", new AppRule(2, 2));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        samsungAppRequest("example.bank");
        assertFalse(engine.after(snapshot, options));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
        assertEquals(java.util.List.of("example.bank"), access.requests);
    }

    @Test public void windowRuleOverridesBothOffOnlyForTheAppSamsungActuallyRequested() throws Exception {
        ProtectionOptions options = ProtectionOptions.NONE.withRule("example.chat", new AppRule(1, 1));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        samsungAppRequest("example.chat");
        assertTrue(engine.after(snapshot, options));
        assertEquals(0, access.policy);

        setup();
        snapshot = engine.before(window, options);
        samsungAppRequest("example.other");
        assertFalse(engine.after(snapshot, options));
        assertEquals(1, access.policy);
    }

    @Test public void appRuleCannotInventProtectionWithoutANativeSelectionRequest() throws Exception {
        ProtectionOptions options = ProtectionOptions.NONE.withRule("example.chat", new AppRule(1, 1));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        assertFalse(engine.after(snapshot, options));
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void independentAppRuleKeepsSplitFullWhileConvertingItsPopup() throws Exception {
        ProtectionOptions options = ProtectionOptions.BOTH.withRule("example.bank", new AppRule(1, 2));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        samsungAppRequest("example.bank");
        assertTrue(engine.after(snapshot, options));
        setup();
        split(window);
        snapshot = engine.before(window, options);
        samsungAppRequest("example.bank");
        assertFalse(engine.after(snapshot, options));
        assertEquals(1, access.policy);
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void appRuleChangedBeforeAfterHookKeepsTheFullRequest() throws Exception {
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, ProtectionOptions.BOTH);
        samsungAppRequest("example.bank");
        ProtectionOptions updated = ProtectionOptions.BOTH.withRule("example.bank", new AppRule(2, 2));
        assertFalse(engine.after(snapshot, updated));
        assertEquals(1, access.policy);
    }

    @Test public void splitCleanupUsesNativeSelectionSourceEvenForAnotherActivityPackage() throws Exception {
        split(window);
        ProtectionOptions options = ProtectionOptions.NONE.withRule("example.bank", new AppRule(1, 1));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        samsungAppRequest("example.bank");
        assertTrue(engine.after(snapshot, options));
        windowSurfaces.prepare(window, options);
        assertTrue(access.surfacePrivacy.get(window.buffer));
        windowSurfaces.prepare(window, options.withRule("example.bank", new AppRule(1, 2)));
        assertFalse(access.surfacePrivacy.get(window.buffer));
    }

    @Test public void ownerRuleDoesNotChangeTheSamePackageInAnotherProfile() throws Exception {
        window.task.user = 10;
        ProtectionOptions options = ProtectionOptions.BOTH.withRule("example.bank", new AppRule(2, 2));
        PopupRoutingEngine.Snapshot snapshot = engine.before(window, options);
        samsungAppRequest("example.bank");
        assertTrue(engine.after(snapshot, options));
    }

    @Test public void allFourConfigurationsRouteOnlyTheirEnabledWindowTypes() throws Exception {
        for (boolean floating : new boolean[]{false, true}) {
            for (boolean splitEnabled : new boolean[]{false, true}) {
                setup();
                ProtectionOptions options = new ProtectionOptions(floating, splitEnabled);
                PopupRoutingEngine.Snapshot popup = engine.before(window, options);
                samsungAppRequest("popup");
                assertEquals(floating, engine.after(popup, options));
                assertEquals(floating, access.partial.contains(target(window)));
                assertEquals(floating ? 0 : 1, access.policy);

                setup();
                split(window);
                PopupRoutingEngine.Snapshot pane = engine.before(window, options);
                samsungAppRequest("pane");
                assertEquals(splitEnabled, engine.after(pane, options));
                assertEquals(splitEnabled, access.partial.contains(target(window)));
                assertEquals(splitEnabled ? 0 : 1, access.policy);
            }
        }
    }

    @Test public void disablingFloatingMidCallbackKeepsFullRequestEvenWhenSplitRemainsOn() throws Exception {
        PopupRoutingEngine.Snapshot s = engine.before(window, ProtectionOptions.BOTH);
        samsungAppRequest("popup");
        assertFalse(engine.after(s, new ProtectionOptions(false, true)));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void disablingSplitMidCallbackKeepsFullRequestEvenWhenFloatingRemainsOn() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = engine.before(window, ProtectionOptions.BOTH);
        samsungAppRequest("pane");
        assertFalse(engine.after(s, new ProtectionOptions(true, false)));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void changingOtherWindowPreferenceDoesNotCancelEligibleConversion() throws Exception {
        PopupRoutingEngine.Snapshot popup = engine.before(window, ProtectionOptions.BOTH);
        samsungAppRequest("popup");
        assertTrue(engine.after(popup, new ProtectionOptions(true, false)));

        setup();
        split(window);
        PopupRoutingEngine.Snapshot pane = engine.before(window, ProtectionOptions.BOTH);
        samsungAppRequest("pane");
        assertTrue(engine.after(pane, new ProtectionOptions(false, true)));
    }

    @Test public void selectedDisabledPaneKeepsFullPrivacyAlongsideEnabledPopup() throws Exception {
        ProtectionOptions options = new ProtectionOptions(true, false);
        PopupRoutingEngine.Snapshot popup = engine.before(window, options);
        samsungAppRequest("popup");
        assertTrue(engine.after(popup, options));
        Window pane = new Window();
        split(pane);
        assertNull(engine.before(pane, options));
        samsungAppRequest("pane");
        assertEquals(Set.of(target(window)), access.partial);
        assertEquals(1, access.policy);
    }

    @Test public void fullscreenPipAndGenericMultiWindowRemainNative() throws Exception {
        for (int mode : new int[]{0, 1, 2, 3, 4, 6, 7}) {
            window.task.mode = mode;
            assertNull(before(window));
        }
    }

    @Test public void selectedSplitPaneQueuesItsDrawableBufferBeforeRemovingFullRequest() throws Exception {
        split(window);
        access.requireBufferBeforeRemovingApp = true;
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("selected-pane");
        assertTrue(after(s));
        assertEquals(Set.of(target(window)), access.partial);
        assertEquals(0, access.policy);
        assertEquals("previous", access.packageName);
        assertEquals(List.of("selected-pane"), access.requests);
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(window.buffer)));
    }

    @Test public void invalidRegionKeepsSamsungFullRequestWithoutCreatingLayer() throws Exception {
        split(window);
        access.regionAvailable = false;
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        assertFalse(after(s));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void eachSelectedWindowReceivesItsOwnBufferRegion() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot first = before(window);
        samsungAppRequest("pane");
        assertTrue(after(first));
        Window second = new Window();
        second.task = window.task;
        PopupRoutingEngine.Snapshot next = before(second);
        samsungAppRequest("dialog");
        assertTrue(after(next));
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(window.buffer)));
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(second.buffer)));
        assertEquals(Set.of(window.task), access.partial);
    }

    @Test public void failedPrivacyLayerWriteRestoresOriginalFullProtection() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        access.failedSurfaceWrites = 1;
        try { after(s); fail("Expected surface transaction failure"); }
        catch (IllegalAccessException expected) { }
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
        assertTrue(access.surfacePrivacy.isEmpty());
    }

    @Test public void unselectedSplitNeighbourStaysUnprotectedInEitherVisitOrder() throws Exception {
        split(window);
        Window neighbour = new Window();
        split(neighbour);
        assertFalse(after(before(neighbour)));
        PopupRoutingEngine.Snapshot selected = before(window);
        samsungAppRequest("selected-pane");
        assertTrue(after(selected));
        assertFalse(after(before(neighbour)));
        assertEquals(Set.of(target(window)), access.partial);
        assertEquals(0, access.policy);
    }

    @Test public void twoSelectedSplitPanesReceiveIndependentCoverage() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot first = before(window);
        samsungAppRequest("first-pane");
        assertTrue(after(first));
        Window second = new Window();
        split(second);
        PopupRoutingEngine.Snapshot next = before(second);
        samsungAppRequest("second-pane");
        assertTrue(after(next));
        assertEquals(Set.of(target(window), target(second)), access.partial);
        assertEquals(List.of("first-pane", "second-pane"), access.requests);
        assertEquals(0, access.policy);
    }

    @Test public void selectedPopupAndSplitPaneCanCoexist() throws Exception {
        PopupRoutingEngine.Snapshot popup = before(window);
        samsungAppRequest("popup");
        assertTrue(after(popup));
        Window pane = new Window();
        split(pane);
        PopupRoutingEngine.Snapshot next = before(pane);
        samsungAppRequest("pane");
        assertTrue(after(next));
        assertEquals(Set.of(target(window), target(pane)), access.partial);
        assertEquals(0, access.policy);
    }

    @Test public void splitOrganizerParentNeverReceivesConvertedPrivacy() throws Exception {
        split(window);
        window.task.leaf = false;
        assertNull(before(window));
    }

    @Test public void splitStageFlagAloneCannotConvertFullscreenOrPip() throws Exception {
        window.task.split = true;
        for (int mode : new int[]{1, 2}) {
            window.task.mode = mode;
            assertNull(before(window));
        }
    }

    @Test public void splitStageOrLeafLossDuringCallbackKeepsFullProtection() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        window.task.split = false;
        assertFalse(after(s));
        window.task.split = true;
        window.task.leaf = false;
        assertFalse(after(s));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void popupSplitTransitionsDuringCallbackKeepFullProtection() throws Exception {
        PopupRoutingEngine.Snapshot popup = before(window);
        samsungAppRequest("popup");
        split(window);
        assertFalse(after(popup));
        PopupRoutingEngine.Snapshot pane = before(window);
        samsungAppRequest("pane");
        window.task.mode = 5;
        window.task.split = false;
        assertFalse(after(pane));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void splitToFullscreenDuringCallbackKeepsFullProtection() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        window.task.mode = 1;
        assertFalse(after(s));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void repeatedSplitTraversalsRebuildTaskCoverageWithoutDuplicates() throws Exception {
        split(window);
        for (int traversal = 0; traversal < 3; traversal++) {
            access.partial.clear();
            access.requests.clear();
            access.policy = 0;
            PopupRoutingEngine.Snapshot s = before(window);
            samsungAppRequest("pane");
            assertTrue(after(s));
            assertEquals(Set.of(target(window)), access.partial);
        }
    }

    @Test public void leavingSplitAllowsNativeTraversalToRestoreFullscreenRouting() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        assertTrue(after(s));
        access.partial.clear(); // Samsung owns clearing/rebuilding coverage each traversal.
        access.requests.clear();
        window.task.mode = 1;
        window.task.split = false;
        assertNull(before(window));
        samsungAppRequest("fullscreen");
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void splitPanePreservesEarlierFullscreenAndSecuredReasons() throws Exception {
        split(window);
        access.policy = 3;
        access.packageName = "earlier";
        access.requests.add("earlier");
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        assertTrue(after(s));
        assertEquals(3, access.policy);
        assertEquals("earlier", access.packageName);
        assertEquals(Set.of(target(window)), access.partial);
    }

    @Test public void securedSplitPaneRetainsSamsungSecuredScreenRouting() throws Exception {
        split(window);
        access.option = 256;
        window.samsungFlags = 4;
        assertNull(before(window));
    }

    @Test public void splitPaneOnExternalDisplayRetainsSamsungRouting() throws Exception {
        split(window);
        window.display = 1;
        assertNull(before(window));
    }

    @Test public void splitSurfaceReplacementDuringCallbackRetainsFullProtection() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        window.task.surface = new Surface();
        assertFalse(after(s));
        assertEquals(1, access.policy);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void failedSplitConversionRestoresSamsungFullProtection() throws Exception {
        split(window);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("pane");
        access.failedPolicyWrites = 1;
        try { after(s); fail("expected injected write failure"); }
        catch (IllegalAccessException expected) { }
        assertEquals(1, access.policy);
        assertEquals("pane", access.packageName);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void unselectedPopupDoesNotGainPrivacyWhenAnotherAppAlreadyRequestsIt() throws Exception {
        access.policy = 1;
        PopupRoutingEngine.Snapshot s = before(window);
        assertFalse(after(s));
        assertTrue(access.partial.isEmpty());
        assertEquals(1, access.policy);
    }

    @Test public void originalVisibilityOrTransitionRejectionRemainsNative() throws Exception {
        assertFalse(after(before(window)));
        assertTrue(access.partial.isEmpty());
    }

    @Test public void underlyingFullscreenAppStillRequestsFullPrivacy() throws Exception {
        access.policy = 1;
        access.packageName = "fullscreen";
        access.requests.add("fullscreen");
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        assertTrue(after(s));
        assertEquals(1, access.policy);
        assertEquals("fullscreen", access.packageName);
    }

    @Test public void anotherSecureWindowRetainsItsGlobalReason() throws Exception {
        access.policy = 2;
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        assertTrue(after(s));
        assertEquals(2, access.policy);
    }

    @Test public void securedPopupWithSecuredScreenOptionIsNotConverted() throws Exception {
        access.option = 256;
        window.samsungFlags = 4;
        assertNull(before(window));
    }

    @Test public void securedFlagDoesNotOverrideUserDisablingSecuredScreenOption() throws Exception {
        window.samsungFlags = 4;
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        assertTrue(after(s));
    }

    @Test public void offAndAlwaysOnModesAreNotModified() throws Exception {
        for (int mode : new int[]{0, 1}) {
            access.mode = mode;
            assertNull(before(window));
        }
    }

    @Test public void externalDisplayIsNotModified() throws Exception {
        window.display = 1;
        assertNull(before(window));
    }

    @Test public void missingTaskOrSurfaceIsNotModified() throws Exception {
        Task task = window.task;
        window.task = null;
        assertNull(before(window));
        window.task = task;
        task.surface = null;
        assertNull(before(window));
    }

    @Test public void invalidSurfaceIsNotModified() throws Exception {
        window.task.surface.valid = false;
        assertNull(before(window));
    }

    @Test public void unknownReasonBitsDisableConversion() throws Exception {
        access.policy = 4;
        assertNull(before(window));
    }

    @Test public void fullscreenTransitionDuringCallbackIsNotModified() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        window.task.mode = 1;
        assertFalse(after(s));
        assertEquals(1, access.policy);
    }

    @Test public void taskReplacementDuringCallbackIsNotModified() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        window.task = new Task();
        assertFalse(after(s));
    }

    @Test public void surfaceReplacementOrDestructionDuringCallbackIsNotModified() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        window.task.surface.valid = false;
        assertFalse(after(s));
        window.task.surface = new Surface();
        assertFalse(after(s));
    }

    @Test public void changedModeOrOptionsAreNotModified() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.mode = 1;
        assertFalse(after(s));
        access.mode = 2;
        access.option = 512;
        assertFalse(after(s));
    }

    @Test public void replacedListOrChangedPrefixAreNotModified() throws Exception {
        access.requests.add("earlier");
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        List<String> original = access.requests;
        access.requests = new ArrayList<>(original);
        assertFalse(after(s));
        access.requests = original;
        access.requests.set(0, "changed");
        assertFalse(after(s));
    }

    @Test public void unexpectedAdditionalRequestsOrGlobalReasonsAreNotModified() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.policy = 3;
        assertFalse(after(s));
        access.policy = 1;
        access.requests.add("unexpected");
        assertFalse(after(s));
    }

    @Test public void twoPopupsCanRequestIndependentTaskCoverage() throws Exception {
        PopupRoutingEngine.Snapshot first = before(window);
        samsungAppRequest("popup1");
        assertTrue(after(first));
        Window second = new Window();
        PopupRoutingEngine.Snapshot next = before(second);
        samsungAppRequest("popup2");
        assertTrue(after(next));
        assertEquals(2, access.partial.size());
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(window.buffer)));
        assertTrue(Boolean.TRUE.equals(access.surfacePrivacy.get(second.buffer)));
        assertEquals(0, access.policy);
    }

    @Test public void repeatedWindowsOfSameTaskDoNotDuplicatePartialTask() throws Exception {
        for (int i = 0; i < 2; i++) {
            PopupRoutingEngine.Snapshot s = before(window);
            samsungAppRequest("popup");
            assertTrue(after(s));
        }
        assertEquals(1, access.partial.size());
    }

    @Test public void closingPopupAllowsNativeNextTraversalToClearCoverage() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        assertTrue(after(s));
        access.partial.clear(); // Samsung beginPostLayoutPolicyLw(), not module-owned persistence.
        access.requests.clear();
        window.task = null;
        assertNull(before(window));
        assertTrue(access.partial.isEmpty());
    }

    @Test public void failedPolicyWriteRollsBackWithoutLosingOriginalProtection() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.failedPolicyWrites = 1;
        try { after(s); fail("expected injected write failure"); }
        catch (IllegalAccessException expected) { /* Simulate a reflective write failure. */ }
        assertEquals(1, access.policy);
        assertEquals("popup", access.packageName);
        assertTrue(access.partial.isEmpty());
    }

    @Test public void failedConversionDoesNotRemovePreexistingPartialCoverage() throws Exception {
        access.partial.add(window.task);
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.failedPolicyWrites = 1;
        try { after(s); fail("expected failure"); }
        catch (IllegalAccessException expected) { }
        assertTrue(access.partial.contains(target(window)));
    }

    @Test public void rollbackFailureRetainsPartialCoverageAndReportsBothFailures() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.failedPolicyWrites = 2;
        try { after(s); fail("expected failure"); }
        catch (IllegalAccessException expected) { assertEquals(1, expected.getSuppressed().length); }
        assertTrue(access.partial.contains(target(window)));
    }

    @Test public void partialCoverageIsQueuedBeforeFullRequestIsRemoved() throws Exception {
        PopupRoutingEngine.Snapshot s = before(window);
        samsungAppRequest("popup");
        access.requirePartialBeforeRemovingApp = true;
        assertTrue(after(s));
    }

    private PopupRoutingEngine.Snapshot before(Window value) throws Exception {
        return engine.before(value, ProtectionOptions.BOTH);
    }

    private boolean after(PopupRoutingEngine.Snapshot snapshot) throws Exception {
        return engine.after(snapshot, ProtectionOptions.BOTH);
    }

    private void samsungAppRequest(String name) {
        access.requests.add(name);
        access.policy |= 1;
        access.packageName = name;
    }

    private static void split(Window window) {
        window.task.mode = 6;
        window.task.split = true;
    }

    private static Task target(Window window) {
        return window.task;
    }

    static final class Surface { boolean valid = true; }
    static final class Task {
        int mode = 5;
        boolean split, leaf = true, requested = true;
        int display, user;
        Surface surface = new Surface();
    }
    static final class Window {
        Task task = new Task();
        Surface buffer = new Surface();
        int display, samsungFlags;
        SplitPrivacyRegion.Bounds bounds = new SplitPrivacyRegion.Bounds(0, 1575, 1440, 3120);
        SplitPrivacyRegion.Bounds displayBounds = new SplitPrivacyRegion.Bounds(0, 0, 1440, 3120);
    }

    static final class FakeAccess implements PolicyAccess {
        final Object lock = new Object();
        int mode = 2, option, policy, failedPolicyWrites;
        boolean requirePartialBeforeRemovingApp;
        String packageName = "previous";
        List<String> requests = new ArrayList<>();
        final Set<Object> partial = new LinkedHashSet<>();
        @Override public Object controller(Object w) { return this; }
        @Override public Object lock(Object c) { return lock; }
        @Override public Object task(Object w) { return ((Window) w).task; }
        @Override public int userId(Object task) { return ((Task) task).user; }
        @Override public int displayId(Object w) { return ((Window) w).display; }
        @Override public int windowingMode(Object t) { return ((Task) t).mode; }
        @Override public boolean splitScreen(Object t) { return ((Task) t).split; }
        @Override public boolean leafTask(Object t) { return ((Task) t).leaf; }
        final java.util.Map<Object, Boolean> surfacePrivacy = new java.util.HashMap<>();
        final java.util.Map<Object, SplitPrivacyRegion> regions = new java.util.HashMap<>();
        int failedSurfaceWrites;
        int surfaceWrites;
        boolean requireBufferBeforeRemovingApp;
        @Override public Object bufferSurface(Object w) { return ((Window) w).buffer; }
        @Override public boolean taskPrivacyRequested(Object t) { return ((Task) t).requested; }
        boolean regionAvailable = true;
        @Override public SplitPrivacyRegion.Bounds windowBounds(Object w) { return ((Window) w).bounds; }
        @Override public SplitPrivacyRegion.Bounds displayBounds(Object w) { return ((Window) w).displayBounds; }
        @Override public SplitPrivacyRegion privacyRegion(Object w) {
            if (!regionAvailable) return null;
            Window window = (Window) w;
            return SplitPrivacyRegion.create(window.bounds, window.displayBounds);
        }
        @Override public void queuePrivacyRegion(Object w, Object surface, SplitPrivacyRegion region) throws IllegalAccessException {
            if (failedSurfaceWrites > 0) { failedSurfaceWrites--; throw new IllegalAccessException(); }
            surfaceWrites++;
            surfacePrivacy.put(surface, region != null);
            regions.put(surface, region);
        }
        @Override public Object surface(Object t) { return ((Task) t).surface; }
        @Override public boolean validSurface(Object s) { return ((Surface) s).valid; }
        @Override public int mode(Object c) { return mode; }
        @Override public int option(Object c) { return option; }
        @Override public int samsungFlags(Object w) { return ((Window) w).samsungFlags; }
        @Override public int appPolicy(Object c) { return policy; }
        @Override public void appPolicy(Object c, int v) throws IllegalAccessException {
            if (failedPolicyWrites > 0) { failedPolicyWrites--; throw new IllegalAccessException(); }
            if (requirePartialBeforeRemovingApp && v == 0) assertFalse(partial.isEmpty());
            if (requireBufferBeforeRemovingApp && v == 0) assertTrue(surfacePrivacy.containsValue(true));
            policy = v;
        }
        @Override public String packageName(Object c) { return packageName; }
        @Override public void packageName(Object c, String v) { packageName = v; }
        @Override public List<?> requests(Object c) { return requests; }
        @Override public Set<Object> partialTasks(Object c) { return partial; }
    }
}
