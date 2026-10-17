package ymg.pwcca.privacydisplayplus;

/** A routing choice never selects an app in Samsung's privacy list. */
record AppRule(int floating, int split, boolean maximum) {
    static final int DEFAULT = 0, WINDOW = 1, FULL_SCREEN = 2;
    static final AppRule INHERIT = new AppRule(DEFAULT, DEFAULT);

    AppRule(int floating, int split) { this(floating, split, false); }

    AppRule {
        validate(floating);
        validate(split);
    }

    private static void validate(int value) {
        if (value < DEFAULT || value > FULL_SCREEN) throw new IllegalArgumentException("Invalid app rule");
    }

    boolean floating(boolean fallback) { return resolve(floating, fallback); }
    boolean split(boolean fallback) { return resolve(split, fallback); }
    boolean isDefault() { return floating == DEFAULT && split == DEFAULT && !maximum; }
    private static boolean resolve(int value, boolean fallback) {
        return value == DEFAULT ? fallback : value == WINDOW;
    }
}
