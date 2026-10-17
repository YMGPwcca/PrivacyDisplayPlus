package ymg.pwcca.privacydisplayplus;

import java.util.Map;
import java.util.TreeMap;

/** One immutable configuration shared by persistence, live control and routing. */
final class ProtectionOptions {
    static final ProtectionOptions BOTH = new ProtectionOptions(true, true);
    static final ProtectionOptions NONE = new ProtectionOptions(false, false);
    final boolean floating;
    final boolean split;
    final Map<String, AppRule> appRules;

    ProtectionOptions(boolean floating, boolean split) {
        this(floating, split, Map.of());
    }

    ProtectionOptions(boolean floating, boolean split, Map<String, AppRule> rules) {
        this.floating = floating;
        this.split = split;
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
        return new ProtectionOptions(rule.floating(floating), rule.split(split));
    }

    ProtectionOptions withRule(String name, AppRule rule) {
        Map<String, AppRule> copy = new TreeMap<>(appRules);
        copy.put(name, rule);
        return new ProtectionOptions(floating, split, copy);
    }

    ProtectionOptions withDefaults(boolean floating, boolean split) {
        return new ProtectionOptions(floating, split, appRules);
    }

    boolean anyEnabled() {
        if (floating || split) return true;
        return appRules.values().stream().anyMatch(rule -> rule.floating() == AppRule.WINDOW || rule.split() == AppRule.WINDOW);
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
        return floating == other.floating && split == other.split && appRules.equals(other.appRules);
    }
}
