package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import java.util.Map;
import java.util.HashMap;
import static org.junit.Assert.*;

public class AppRulesTest {
    private static final String APP = "example.bank";

    @Test public void maximumOnlyRuleIsRetainedWithoutEnablingWindowRouting() {
        ProtectionOptions options = ProtectionOptions.NONE.withRule(APP, new AppRule(0, 0, true));
        assertTrue(options.hasMaximumRules());
        assertTrue(options.maximumFor(APP));
        assertFalse(options.maximumFor("example.other"));
        assertFalse(options.anyEnabled());
        assertFalse(options.forApp(APP, 0).anyEnabled());
        assertTrue(options.withDefaults(true, false).maximumFor(APP));
        assertFalse(options.sameAs(ProtectionOptions.NONE));
        assertFalse(options.withRule(APP, AppRule.INHERIT).hasMaximumRules());
    }

    @Test public void independentAppChoicesOverrideOnlyTheirOwnWindowMode() {
        ProtectionOptions options = new ProtectionOptions(true, false, Map.of(APP,
                new AppRule(AppRule.FULL_SCREEN, AppRule.WINDOW)));
        assertFalse(options.forApp(APP, 0).allows(5));
        assertTrue(options.forApp(APP, 0).allows(6));
        assertTrue(options.forApp("example.other", 0).allows(5));
        assertFalse(options.forApp("example.other", 0).allows(6));
        assertFalse(options.forApp(APP, 0).allows(1));
    }

    @Test public void appRulesNeverLeakIntoAnotherProfile() {
        ProtectionOptions options = ProtectionOptions.BOTH.withRule(APP, new AppRule(2, 2));
        assertTrue(options.forApp(APP, 10).sameAs(options));
        assertFalse(options.forApp(APP, 0).anyEnabled());
    }

    @Test public void defaultFollowsLaterGlobalChangesWithoutLosingAppRule() {
        ProtectionOptions options = ProtectionOptions.BOTH.withRule(APP, new AppRule(0, 2));
        ProtectionOptions changed = options.withDefaults(false, true);
        assertFalse(changed.forApp(APP, 0).floating);
        assertFalse(changed.forApp(APP, 0).split);
        assertEquals(new AppRule(0, 2), changed.appRules.get(APP));
    }

    @Test public void explicitWindowRuleCanRunWithBothGlobalSwitchesOff() {
        ProtectionOptions options = ProtectionOptions.NONE.withRule(APP, new AppRule(1, 0));
        assertTrue(options.anyEnabled());
        assertTrue(options.mayRoute(5));
        assertFalse(options.mayRoute(6));
        assertTrue(options.forApp(APP, 0).floating);
        assertFalse(options.forApp("example.other", 0).floating);
    }

    @Test public void clearingRuleRemovesOnlyThatAppOverride() {
        ProtectionOptions options = ProtectionOptions.BOTH.withRule(APP, new AppRule(2, 2))
                .withRule("example.chat", new AppRule(1, 2)).withRule(APP, AppRule.INHERIT);
        assertFalse(options.appRules.containsKey(APP));
        assertTrue(options.appRules.containsKey("example.chat"));
    }

    @Test public void configurationsCopyCallerMapsAndCannotBeMutatedAfterPublication() {
        Map<String, AppRule> caller = new HashMap<>();
        caller.put(APP, new AppRule(2, 1));
        ProtectionOptions options = new ProtectionOptions(true, true, caller);
        caller.clear();
        assertEquals(1, options.appRules.size());
        try { options.appRules.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void equalityIncludesRulesAndNullWindowPackageUsesDefaults() {
        assertFalse(ProtectionOptions.BOTH.sameAs(ProtectionOptions.BOTH.withRule(APP, new AppRule(2, 2))));
        assertSame(ProtectionOptions.BOTH, ProtectionOptions.BOTH.forApp(null, 0));
    }

    @Test(expected = IllegalArgumentException.class) public void unknownRuleIsRejected() { new AppRule(3, 0); }
    @Test(expected = IllegalArgumentException.class) public void negativeRuleIsRejected() { new AppRule(0, -1); }
    @Test(expected = IllegalArgumentException.class) public void malformedPackageIsRejected() {
        ProtectionOptions.BOTH.withRule("example.bank\nother", new AppRule(1, 1));
    }
}
