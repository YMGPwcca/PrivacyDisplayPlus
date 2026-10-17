package ymg.pwcca.privacydisplayplus;

/** Hook names are bound to the locally inspected firmware, not guessed across OTAs. */
final class FirmwareProfile {
    static final String FINGERPRINT =
            "samsung/m3qxxx/m3q:17/CP2A.260605.016/S948BXXS4BZIG_OXM4BZIG:user/release-keys";
    static final String VISITOR = "com.android.server.wm.DisplayContent$$ExternalSyntheticLambda3";
    static final String VISIT_METHOD =
            "accept$com$android$server$wm$DisplayContent$$ExternalSyntheticLambda42";

    private FirmwareProfile() {}

    static boolean supports(int sdk, String fingerprint) {
        return sdk == 37 && FINGERPRINT.equals(fingerprint);
    }
}
