package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class PanelPrivacyLevelTest {
    @Test public void menuLevelsMapToExactlyOffOnAndMaximum() {
        assertEquals(PanelPrivacyLevel.OFF, PanelPrivacyLevel.fromMenu(0));
        assertEquals(PanelPrivacyLevel.ON, PanelPrivacyLevel.fromMenu(1));
        assertEquals(PanelPrivacyLevel.MAXIMUM, PanelPrivacyLevel.fromMenu(2));

        for (PanelPrivacyLevel level : PanelPrivacyLevel.values()) {
            assertEquals(level, PanelPrivacyLevel.fromSaved(level.enabled, level.maximum));
        }
    }

    @Test public void disabledLegacyMaximumMapsToOffInsteadOfEnablingPrivacy() {
        assertEquals(PanelPrivacyLevel.OFF, PanelPrivacyLevel.fromSaved(false, true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownMenuPositionIsRejected() { PanelPrivacyLevel.fromMenu(3); }
}
