package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class HandleCallerTest {
    @Test public void systemUiNeedsItsRealUidAndOsSharedPackageIdentity() {
        assertTrue(HandleCaller.trusted(10049, "com.android.systemui", 10049));
        assertFalse(HandleCaller.trusted(10049, "example.other", 10049));
        assertFalse(HandleCaller.trusted(10049, null, 10049));
        assertFalse(HandleCaller.trusted(-1, "com.android.systemui", 10049));
        assertFalse(HandleCaller.trusted(10158, "com.android.systemui", 10049));
    }

    @Test public void unknownExpectedIdentityAlwaysFailsClosed() {
        assertFalse(HandleCaller.trusted(-1, "com.android.systemui", -1));
    }

    @Test public void sharedSystemUidDoesNotAuthorizeAnotherPackage() {
        assertFalse(HandleCaller.trusted(1000, "android", 1000));
        assertTrue(HandleCaller.trusted(1000, "com.android.systemui", 1000));
    }
}
