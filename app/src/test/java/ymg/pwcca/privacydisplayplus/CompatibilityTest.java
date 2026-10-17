package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilityTest {
    @Test public void onlyInspectedSdkAndFingerprintAreEnabled() {
        assertTrue(FirmwareProfile.supports(37, FirmwareProfile.FINGERPRINT));
        assertFalse(FirmwareProfile.supports(36, FirmwareProfile.FINGERPRINT));
        assertFalse(FirmwareProfile.supports(38, FirmwareProfile.FINGERPRINT));
        assertFalse(FirmwareProfile.supports(37, FirmwareProfile.FINGERPRINT + ".ota"));
        assertFalse(FirmwareProfile.supports(37, null));
    }

    @Test public void inheritedFrameworkFieldsAndMethodsResolve() throws Exception {
        assertEquals(Parent.class, ReflectivePolicyAccess.field(Child.class, "value", int.class).getDeclaringClass());
        assertEquals(Parent.class, ReflectivePolicyAccess.method(Child.class, "inherited").getDeclaringClass());
    }

    @Test(expected = NoSuchFieldException.class) public void wrongFieldTypeRejectsSchema() throws Exception {
        ReflectivePolicyAccess.field(Child.class, "value", String.class);
    }

    @Test(expected = NoSuchFieldException.class) public void missingFieldRejectsSchema() throws Exception {
        ReflectivePolicyAccess.field(Child.class, "missing", int.class);
    }

    @Test(expected = NoSuchMethodException.class) public void changedSignatureRejectsSchema() throws Exception {
        ReflectivePolicyAccess.method(Child.class, "inherited", int.class);
    }

    static class Parent { private int value; private void inherited() { } }
    static class Child extends Parent { }
}
