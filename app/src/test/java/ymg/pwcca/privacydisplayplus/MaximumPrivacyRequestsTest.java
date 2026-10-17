package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import java.util.Set;
import static org.junit.Assert.*;
import static ymg.pwcca.privacydisplayplus.PopupRoutingEngineTest.*;

public class MaximumPrivacyRequestsTest {
    private final FakeAccess access = new FakeAccess();
    private final MaximumPrivacyRequests requests = new MaximumPrivacyRequests(access);
    private final Window window = new Window();
    private final ProtectionOptions maximum = ProtectionOptions.NONE.withRule("example.bank", new AppRule(0, 0, true));

    @Test public void quickExitAndReentryPublishSourceBeforeFirstNativeEnableCanReadIt() throws Exception {
        window.task.mode = 1;
        for (int i = 0; i < 20; i++) {
            requests.begin();
            access.requests.clear();
            contribute(window, "example.bank", false, false);
            requests.finish(); // Called BEFORE native finish can enqueue its enable message.
            assertFalse(requests.wantsMaximum(maximum, false, Set.of()));
            assertTrue(requests.wantsMaximum(maximum, true, Set.of())); // First native enable, no second traversal.
            requests.begin();
            access.requests.clear();
            requests.finish();
            assertFalse(requests.wantsMaximum(maximum, true, Set.of())); // An old applied-on flag cannot retain Maximum.
        }
    }

    private void contribute(Window target, String source, boolean partial, boolean suppressed) throws Exception {
        MaximumPrivacyRequests.Snapshot snapshot = requests.before(target);
        access.requests.add(source);
        if (partial) access.partial.add(target.task);
        requests.after(snapshot, suppressed, partial);
    }

    @Test public void fullscreenMaximumRequiresBothNativeRequestAndFullProtection() throws Exception {
        window.task.mode = 1;
        contribute(window, "example.bank", false, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, false, Set.of()));
        assertTrue(requests.wantsMaximum(maximum, true, Set.of()));
        assertFalse(requests.wantsMaximum(ProtectionOptions.BOTH, true, Set.of()));
    }

    @Test public void regionalRequestNeedsFinalNativeApplicationEvenWhenOtherAppProtectsFullScreen() throws Exception {
        contribute(window, "example.bank", true, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of()));
        assertTrue(requests.wantsMaximum(maximum, false, Set.of(window.task)));
        assertFalse(requests.wantsMaximum(maximum, false, Set.of(new Task())));
    }

    @Test public void fullFallbackIsRetainedEvenWhenSameTaskAlreadyHasAnotherRegionalRequest() throws Exception {
        access.partial.add(window.task);
        contribute(window, "example.bank", false, false);
        requests.finish();
        assertTrue(requests.wantsMaximum(maximum, true, Set.of()));
        assertFalse(requests.wantsMaximum(maximum, false, Set.of()));
    }

    @Test public void lastMaximumWindowLeavingRestoresDefaultOnNextCompletedLayout() throws Exception {
        contribute(window, "example.bank", true, false);
        requests.finish();
        assertTrue(requests.wantsMaximum(maximum, false, Set.of(window.task)));
        requests.begin();
        assertTrue(requests.wantsMaximum(maximum, false, Set.of(window.task)));
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }

    @Test public void oneMaximumAppWinsRegardlessOfWindowVisitOrder() throws Exception {
        for (boolean bankFirst : new boolean[]{true, false}) {
            requests.begin();
            Window other = new Window();
            contribute(bankFirst ? window : other, bankFirst ? "example.bank" : "example.chat", true, false);
            contribute(bankFirst ? other : window, bankFirst ? "example.chat" : "example.bank", true, false);
            requests.finish();
            assertTrue(requests.wantsMaximum(maximum, false, Set.of(window.task, other.task)));
            assertFalse(requests.wantsMaximum(maximum, false, Set.of(other.task)));
        }
    }

    @Test public void rootAppSourceIsUsedInsteadOfVisibleWindowPackage() throws Exception {
        contribute(window, "example.chat", true, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, false, Set.of(window.task)));
    }

    @Test public void sessionOffCannotContributeDespitePreservedSamsungBookkeeping() throws Exception {
        contribute(window, "example.bank", false, true);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }

    @Test public void hiddenOrUnselectedWindowWithoutNativeAppendCannotContribute() throws Exception {
        MaximumPrivacyRequests.Snapshot snapshot = requests.before(window);
        requests.after(snapshot, false, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }

    @Test public void replacedListOrChangedPrefixIsRejected() throws Exception {
        access.requests.add("example.old");
        MaximumPrivacyRequests.Snapshot snapshot = requests.before(window);
        access.requests.set(0, "example.changed");
        access.requests.add("example.bank");
        requests.after(snapshot, false, false);
        snapshot = requests.before(window);
        access.requests = new java.util.ArrayList<>(access.requests);
        access.requests.add("example.bank");
        requests.after(snapshot, false, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }

    @Test public void lifecycleChangesBetweenCallbacksCannotLeaveMaximumEnabled() throws Exception {
        MaximumPrivacyRequests.Snapshot snapshot = requests.before(window);
        window.task.mode = 1;
        access.requests.add("example.bank");
        requests.after(snapshot, false, false);
        snapshot = requests.before(window);
        window.buffer = new Surface();
        access.requests.add("example.bank");
        requests.after(snapshot, false, false);
        requests.finish();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }

    @Test public void unsupportedDisplaysProfilesAndWindowModesAreExcluded() throws Exception {
        window.task.user = 10;
        assertNull(requests.before(window));
        window.task.user = 0;
        window.display = 1;
        assertNull(requests.before(window));
        window.display = 0;
        window.task.mode = 2;
        assertNull(requests.before(window));
        window.task.mode = 6;
        assertNull(requests.before(window));
        window.task.split = true;
        window.task.leaf = false;
        assertNull(requests.before(window));
        window.task.leaf = true;
        assertNotNull(requests.before(window));
    }

    @Test public void changedRulesAndFailureClearReleaseCommittedRequests() throws Exception {
        contribute(window, "example.bank", true, false);
        requests.finish();
        assertTrue(requests.wantsMaximum(maximum, false, Set.of(window.task)));
        assertFalse(requests.wantsMaximum(maximum.withRule("example.bank", AppRule.INHERIT), false, Set.of(window.task)));
        requests.clear();
        assertFalse(requests.wantsMaximum(maximum, true, Set.of(window.task)));
    }
}
