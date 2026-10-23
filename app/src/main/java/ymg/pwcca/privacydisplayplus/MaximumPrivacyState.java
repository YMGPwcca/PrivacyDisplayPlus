package ymg.pwcca.privacydisplayplus;

/** Native suppression is authoritative even when an app has a Maximum rule. */
record MaximumPrivacyState(int userId, int mode, int displayState, int temporaryOff, int fixedOff) {
    static boolean needsLevelWrite(boolean previousMaximum, boolean maximum, boolean requestedFull, boolean appliedFull) {
        // A queued full enable/disable will carry the latest level itself. A level-only write
        // must not overtake it using the old applied-full flag.
        return previousMaximum != maximum && requestedFull == appliedFull;
    }

    static boolean secureEntryMaximum(boolean selected, int nativeOptions, int nativeReasons,
            boolean requestedFull) {
        // Samsung's secured-screen option (256) must have supplied the native secure reason (2).
        return selected && requestedFull && (nativeOptions & 256) != 0 && (nativeReasons & 2) != 0;
    }

    boolean allowsOverride() {
        if (userId != 0 || mode != 2 || displayState != 2) return false;
        // Display off/AOD, sleep, and an explicit native off token.
        return (temporaryOff & (16 | 32)) == 0 && (fixedOff & 262144) == 0;
    }
}
