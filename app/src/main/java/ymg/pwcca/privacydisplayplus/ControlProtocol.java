package ymg.pwcca.privacydisplayplus;

import android.os.Bundle;

final class ControlProtocol {
    static final String PACKAGE = "ymg.pwcca.privacydisplayplus";
    static final String PERMISSION = PACKAGE + ".permission.CONTROL";
    static final String AUTHORITY = PACKAGE + ".config";
    static final String QUERY = PACKAGE + ".QUERY";
    static final String SET = PACKAGE + ".SET";
    static final String APPS = PACKAGE + ".APPS";
    static final String HANDLE_QUERY = PACKAGE + ".HANDLE_QUERY", HANDLE_SET = PACKAGE + ".HANDLE_SET";
    static final String DRAG_QUERY = PACKAGE + ".DRAG_QUERY";
    static final String DRAG_STATE = PACKAGE + ".DRAG_STATE", DRAG_ACTIVE = "drag_active";
    static final String DRAG_ID = "drag_id";
    static final String SURFACE = "surface", WINDOW_BOUNDS = "window_bounds", DISPLAY_BOUNDS = "display_bounds";
    static final String TASK_BOUNDS = "task_bounds";
    static final String TASK_TOKEN = "task_token", OVERRIDE = "override", OVERRIDE_COUNT = "override_count";
    static final String RULE_PACKAGES = "rule_packages", RULE_FLOATING = "rule_floating", RULE_SPLIT = "rule_split";
    static final String RULE_MAXIMUM = "rule_maximum";
    static final String ITEMS = "items", PROTECTED = "protected", ERROR = "error";
    static final String TEMP_COUNT = "temporary_count", NATIVE_ALLOWED = "native_allowed";
    static final String GET_CONFIG = "get_config";
    static final String ENABLED = "enabled";
    static final String FLOATING = "floating";
    static final String SPLIT = "split";
    static final String READY = "ready";
    static final String HEALTHY = "healthy";
    static final String VERSION = "version";
    // Earlier hooks cannot apply per-app Maximum privacy rules.
    static final int VERSION_CODE = 15;

    static boolean hasOptions(Bundle bundle) {
        return bundle != null && bundle.containsKey(FLOATING) && bundle.containsKey(SPLIT)
                && bundle.containsKey(RULE_PACKAGES) && bundle.containsKey(RULE_FLOATING)
                && bundle.containsKey(RULE_SPLIT) && bundle.containsKey(RULE_MAXIMUM);
    }

    static ProtectionOptions readOptions(Bundle bundle) {
        if (!hasOptions(bundle)) throw new IllegalArgumentException("Missing window preferences");
        String[] names = bundle.getStringArray(RULE_PACKAGES);
        int[] floating = bundle.getIntArray(RULE_FLOATING), split = bundle.getIntArray(RULE_SPLIT);
        boolean[] maximum = bundle.getBooleanArray(RULE_MAXIMUM);
        validateRules(names, floating, split, maximum);
        java.util.Map<String, AppRule> rules = new java.util.TreeMap<>();
        for (int i = 0; i < names.length; i++) {
            if (rules.put(names[i], new AppRule(floating[i], split[i], maximum[i])) != null) {
                throw new IllegalArgumentException("Duplicate app rule");
            }
        }
        return new ProtectionOptions(bundle.getBoolean(FLOATING), bundle.getBoolean(SPLIT), rules);
    }

    private static void validateRules(String[] names, int[] floating, int[] split, boolean[] maximum) {
        if (names == null || floating == null || split == null || maximum == null) {
            throw new IllegalArgumentException("Missing app rule arrays");
        }
        int count = names.length;
        if (count > 256 || count != floating.length || count != split.length || count != maximum.length) {
            throw new IllegalArgumentException("Invalid app rule lengths");
        }
    }

    static void writeOptions(Bundle bundle, ProtectionOptions options) {
        bundle.putBoolean(FLOATING, options.floating);
        bundle.putBoolean(SPLIT, options.split);
        String[] names = options.appRules.keySet().toArray(new String[0]);
        int[] floating = new int[names.length], split = new int[names.length];
        boolean[] maximum = new boolean[names.length];
        for (int i = 0; i < names.length; i++) {
            AppRule rule = options.appRules.get(names[i]);
            floating[i] = rule.floating();
            split[i] = rule.split();
            maximum[i] = rule.maximum();
        }
        bundle.putStringArray(RULE_PACKAGES, names);
        bundle.putIntArray(RULE_FLOATING, floating);
        bundle.putIntArray(RULE_SPLIT, split);
        bundle.putBooleanArray(RULE_MAXIMUM, maximum);
    }
    private ControlProtocol() {}
}
