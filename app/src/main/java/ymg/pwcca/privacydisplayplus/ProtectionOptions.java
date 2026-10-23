package ymg.pwcca.privacydisplayplus;

import java.util.Map;
import java.util.TreeMap;

/** One immutable configuration shared by persistence, live control and routing. */
final class ProtectionOptions {
    static final ProtectionOptions BOTH = new ProtectionOptions(true, true, false, false, false, false, Map.of());
    static final ProtectionOptions NONE = new ProtectionOptions(false, false, false, false, false, false, Map.of());
    final boolean floating;
    final boolean split;
    final boolean notificationCenter;
    final boolean quickSettings;
    final boolean notificationCenterMaximum;
    final boolean quickSettingsMaximum;
    final boolean lockScreen;
    final boolean lockScreenMaximum;
    final Map<String, AppRule> appRules;

    ProtectionOptions(boolean floating, boolean split) {
        this(floating, split, false, false, false, false, Map.of());
    }

    ProtectionOptions(boolean floating, boolean split, Map<String, AppRule> rules) {
        this(floating, split, false, false, false, false, rules);
    }

    ProtectionOptions(boolean floating, boolean split, boolean notificationCenter, boolean quickSettings,
                      boolean notificationCenterMaximum, boolean quickSettingsMaximum, Map<String, AppRule> rules) {
        this(floating, split, notificationCenter, quickSettings, notificationCenterMaximum,
                quickSettingsMaximum, false, false, rules);
    }

    ProtectionOptions(boolean floating, boolean split, boolean notificationCenter, boolean quickSettings,
                      boolean notificationCenterMaximum, boolean quickSettingsMaximum,
                      boolean lockScreen, boolean lockScreenMaximum, Map<String, AppRule> rules) {
        this.floating = floating;
        this.split = split;
        this.notificationCenter = notificationCenter;
        this.quickSettings = quickSettings;
        this.notificationCenterMaximum = notificationCenterMaximum;
        this.quickSettingsMaximum = quickSettingsMaximum;
        this.lockScreen = lockScreen;
        this.lockScreenMaximum = lockScreenMaximum;
        if (rules.size() > 256) throw new IllegalArgumentException("Too many app rules");
        TreeMap<String, AppRule> copy = new TreeMap<>();
        rules.forEach((name, rule) -> {
            validatePackage(name);
            if (rule == null) throw new IllegalArgumentException("Missing app rule");
            if (!rule.isDefault()) copy.put(name, rule);
        });
        this.appRules = java.util.Collections.unmodifiableMap(copy);
    }

    static void validatePackage(String name) {
        if (name == null || name.length() > 255 || !name.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)*")) {
            throw new IllegalArgumentException("Invalid package name");
        }
    }

    ProtectionOptions forApp(String name, int userId) {
        if (name == null) return this;
        AppRule rule = userId == 0 ? appRules.get(name) : null;
        if (rule == null) return this;
        return new ProtectionOptions(rule.floating(floating), rule.split(split), notificationCenter, quickSettings,
                notificationCenterMaximum, quickSettingsMaximum, lockScreen, lockScreenMaximum, Map.of());
    }

    ProtectionOptions withPanelPrivacy(boolean notificationCenter, boolean quickSettings,
                                       boolean notificationCenterMaximum, boolean quickSettingsMaximum) {
        return new ProtectionOptions(floating, split, notificationCenter, quickSettings, notificationCenterMaximum,
                quickSettingsMaximum, lockScreen, lockScreenMaximum, appRules);
    }

    ProtectionOptions withLockScreenPrivacy(boolean enabled, boolean maximum) {
        return new ProtectionOptions(floating, split, notificationCenter, quickSettings, notificationCenterMaximum,
                quickSettingsMaximum, enabled, maximum, appRules);
    }

    ProtectionOptions withRule(String name, AppRule rule) {
        Map<String, AppRule> copy = new TreeMap<>(appRules);
        copy.put(name, rule);
        return new ProtectionOptions(floating, split, notificationCenter, quickSettings, notificationCenterMaximum,
                quickSettingsMaximum, lockScreen, lockScreenMaximum, copy);
    }

    ProtectionOptions withDefaults(boolean floating, boolean split) {
        return new ProtectionOptions(floating, split, notificationCenter, quickSettings, notificationCenterMaximum,
                quickSettingsMaximum, lockScreen, lockScreenMaximum, appRules);
    }

    boolean anyEnabled() {
        if (floating || split || notificationCenter || quickSettings || lockScreen) return true;
        return appRules.values().stream().anyMatch(rule -> rule.floating() == AppRule.WINDOW || rule.split() == AppRule.WINDOW);
    }

    boolean hasMaximumRules() { return appRules.values().stream().anyMatch(AppRule::maximum); }

    boolean maximumFor(String name) {
        AppRule rule = appRules.get(name);
        return rule != null && rule.maximum();
    }

    boolean allows(int windowingMode) {
        return switch (windowingMode) {
            case 5 -> floating;
            case 6 -> split;
            default -> false;
        };
    }

    boolean mayRoute(int windowingMode) {
        if (allows(windowingMode)) return true;
        if (windowingMode == 5) return appRules.values().stream().anyMatch(rule -> rule.floating() == AppRule.WINDOW);
        if (windowingMode == 6) return appRules.values().stream().anyMatch(rule -> rule.split() == AppRule.WINDOW);
        return false;
    }

    boolean sameAs(ProtectionOptions other) {
        return floating == other.floating && split == other.split
                && notificationCenter == other.notificationCenter && quickSettings == other.quickSettings
                && notificationCenterMaximum == other.notificationCenterMaximum
                && quickSettingsMaximum == other.quickSettingsMaximum
                && lockScreen == other.lockScreen && lockScreenMaximum == other.lockScreenMaximum
                && appRules.equals(other.appRules);
    }
}
