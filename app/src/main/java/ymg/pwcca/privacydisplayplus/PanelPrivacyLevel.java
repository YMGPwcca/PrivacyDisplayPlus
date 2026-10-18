package ymg.pwcca.privacydisplayplus;

enum PanelPrivacyLevel {
    OFF(0, false, false), ON(1, true, false), MAXIMUM(2, true, true);

    final int menuIndex;
    final boolean enabled;
    final boolean maximum;

    PanelPrivacyLevel(int menuIndex, boolean enabled, boolean maximum) {
        this.menuIndex = menuIndex;
        this.enabled = enabled;
        this.maximum = maximum;
    }

    static PanelPrivacyLevel fromSaved(boolean enabled, boolean maximum) {
        if (!enabled) return OFF;
        return maximum ? MAXIMUM : ON;
    }

    static PanelPrivacyLevel fromMenu(int position) {
        for (PanelPrivacyLevel value : values()) if (value.menuIndex == position) return value;
        throw new IllegalArgumentException("Unknown panel privacy level");
    }
}
