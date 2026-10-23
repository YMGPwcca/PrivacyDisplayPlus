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
    static final String SHADE_QUERY = PACKAGE + ".SHADE_QUERY", SHADE_STATE = PACKAGE + ".SHADE_STATE";
    static final String SHADE_OWNER = "shade_owner", SHADE_READY = "shade_ready";
    static final String NOTIFICATION_CENTER_OPEN = "notification_center_open";
    static final String QUICK_SETTINGS_OPEN = "quick_settings_open";
    static final String LOCK_SCREEN_OPEN = "lock_screen_open";
    static final String SURFACE = "surface", WINDOW_BOUNDS = "window_bounds", DISPLAY_BOUNDS = "display_bounds";
    static final String TASK_BOUNDS = "task_bounds";
    static final String CORNER_RADIUS = "corner_radius";
    static final String TASK_TOKEN = "task_token", OVERRIDE = "override", OVERRIDE_COUNT = "override_count";
    static final String RULE_PACKAGES = "rule_packages", RULE_FLOATING = "rule_floating", RULE_SPLIT = "rule_split";
    static final String RULE_MAXIMUM = "rule_maximum";
    static final String ITEMS = "items", PROTECTED = "protected", ERROR = "error";
    static final String TEMP_COUNT = "temporary_count", NATIVE_ALLOWED = "native_allowed";
    static final String GET_CONFIG = "get_config";
    static final String ENABLED = "enabled";
    static final String FLOATING = "floating";
    static final String SPLIT = "split";
    static final String NOTIFICATION_CENTER = "notification_center";
    static final String QUICK_SETTINGS = "quick_settings";
    static final String NOTIFICATION_CENTER_MAXIMUM = "notification_center_maximum";
    static final String QUICK_SETTINGS_MAXIMUM = "quick_settings_maximum";
    static final String LOCK_SCREEN = "lock_screen", LOCK_SCREEN_MAXIMUM = "lock_screen_maximum";
    static final String SECURE_ENTRY_MAXIMUM = "secure_entry_maximum";
    static final String READY = "ready";
    static final String HEALTHY = "healthy";
    static final String VERSION = "version";
    // Adds Maximum for Samsung's native secure-entry condition.
    static final int VERSION_CODE = 22;

    static boolean hasOptions(Bundle bundle) {
        return bundle != null && bundle.containsKey(FLOATING) && bundle.containsKey(SPLIT)
                && bundle.containsKey(NOTIFICATION_CENTER) && bundle.containsKey(QUICK_SETTINGS)
                && bundle.containsKey(NOTIFICATION_CENTER_MAXIMUM) && bundle.containsKey(QUICK_SETTINGS_MAXIMUM)
                && bundle.containsKey(LOCK_SCREEN) && bundle.containsKey(LOCK_SCREEN_MAXIMUM)
                && bundle.containsKey(SECURE_ENTRY_MAXIMUM)
                && bundle.containsKey(RULE_PACKAGES) && bundle.containsKey(RULE_FLOATING)
                && bundle.containsKey(RULE_SPLIT) && bundle.containsKey(RULE_MAXIMUM);
    }

    static ProtectionOptions readOptions(Bundle bundle) {
        if (!hasOptions(bundle)) throw new IllegalArgumentException("Missing window or system-panel preferences");
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
        return new ProtectionOptions(bundle.getBoolean(FLOATING), bundle.getBoolean(SPLIT),
                bundle.getBoolean(NOTIFICATION_CENTER), bundle.getBoolean(QUICK_SETTINGS),
                bundle.getBoolean(NOTIFICATION_CENTER_MAXIMUM), bundle.getBoolean(QUICK_SETTINGS_MAXIMUM),
                bundle.getBoolean(LOCK_SCREEN), bundle.getBoolean(LOCK_SCREEN_MAXIMUM),
                bundle.getBoolean(SECURE_ENTRY_MAXIMUM), rules);
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
        bundle.putBoolean(NOTIFICATION_CENTER, options.notificationCenter);
        bundle.putBoolean(QUICK_SETTINGS, options.quickSettings);
        bundle.putBoolean(NOTIFICATION_CENTER_MAXIMUM, options.notificationCenterMaximum);
        bundle.putBoolean(QUICK_SETTINGS_MAXIMUM, options.quickSettingsMaximum);
        bundle.putBoolean(LOCK_SCREEN, options.lockScreen);
        bundle.putBoolean(LOCK_SCREEN_MAXIMUM, options.lockScreenMaximum);
        bundle.putBoolean(SECURE_ENTRY_MAXIMUM, options.secureEntryMaximum);
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
