package ymg.pwcca.privacydisplayplus;

/** OS-provided shared identity is required, including for shared system UIDs. */
final class HandleCaller {
    static final String PACKAGE = "com.android.systemui";
    static boolean trusted(int senderUid, String senderPackage, int expectedUid) {
        return expectedUid >= 0 && senderUid == expectedUid && PACKAGE.equals(senderPackage);
    }
    private HandleCaller() {}
}
