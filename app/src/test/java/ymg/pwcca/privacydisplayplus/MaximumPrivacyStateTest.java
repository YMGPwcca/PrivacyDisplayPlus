package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class MaximumPrivacyStateTest {
    @Test public void credentialMaximumRequiresSamsungSecureOptionAndNativeReason() {
        assertTrue(MaximumPrivacyState.secureEntryMaximum(true, 256, 2, true));
        assertTrue(MaximumPrivacyState.secureEntryMaximum(true, 258, 3, true));
        assertFalse(MaximumPrivacyState.secureEntryMaximum(false, 256, 2, true));
        assertFalse(MaximumPrivacyState.secureEntryMaximum(true, 0, 2, true));
        assertFalse(MaximumPrivacyState.secureEntryMaximum(true, 256, 1, true));
        assertFalse(MaximumPrivacyState.secureEntryMaximum(true, 256, 2, false));
    }

    @Test public void maximumLevelWriteCannotOvertakeAPendingFullEnableOrDisable() {
        assertFalse(MaximumPrivacyState.needsLevelWrite(false, true, true, false));
        assertFalse(MaximumPrivacyState.needsLevelWrite(true, false, false, true));
        assertFalse(MaximumPrivacyState.needsLevelWrite(true, true, true, true));
        assertTrue(MaximumPrivacyState.needsLevelWrite(false, true, true, true));
        assertTrue(MaximumPrivacyState.needsLevelWrite(true, false, true, true));
        assertTrue(MaximumPrivacyState.needsLevelWrite(false, true, false, false));
        assertTrue(MaximumPrivacyState.needsLevelWrite(true, false, false, false));
    }

    @Test public void onlyMainProfileConditionalModeOnActiveDisplayAllowsOverride() {
        assertTrue(new MaximumPrivacyState(0, 2, 2, 0, 0).allowsOverride());
        assertFalse(new MaximumPrivacyState(10, 2, 2, 0, 0).allowsOverride());
        assertFalse(new MaximumPrivacyState(0, 0, 2, 0, 0).allowsOverride());
        assertFalse(new MaximumPrivacyState(0, 1, 2, 0, 0).allowsOverride());
        for (int displayState : new int[]{0, 1, 3, 4, 5, 6}) {
            assertFalse(new MaximumPrivacyState(0, 2, displayState, 0, 0).allowsOverride());
        }
    }

    @Test public void nativeAodSleepAndOffTokensAlwaysWin() {
        for (int mask : new int[]{16, 32, 48}) {
            assertFalse(new MaximumPrivacyState(0, 2, 2, mask, 0).allowsOverride());
        }
        assertFalse(new MaximumPrivacyState(0, 2, 2, 0, 262144).allowsOverride());
        assertTrue(new MaximumPrivacyState(0, 2, 2, 0, 65536).allowsOverride());
        // Manual-off bookkeeping is resolved by Samsung's final full/partial requests, not bypassed here.
    }
}
