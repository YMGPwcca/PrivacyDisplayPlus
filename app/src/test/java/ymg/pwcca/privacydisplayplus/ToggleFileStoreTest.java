package ymg.pwcca.privacydisplayplus;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class ToggleFileStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void maximumOnlyAndMixedRulesSurviveReopening() throws Exception {
        File target = new File(temporary.getRoot(), "maximum");
        ProtectionOptions options = ProtectionOptions.NONE.withRule("example.bank", new AppRule(0, 0, true))
                .withRule("example.chat", new AppRule(1, 2, false));
        assertTrue(new ToggleFileStore(target).save(options));
        assertTrue(new ToggleFileStore(target).read().sameAs(options));
    }

    @Test public void oldRuleFormatMigratesWithoutEnablingMaximum() throws Exception {
        File target = new File(temporary.getRoot(), "old-rules");
        try (var data = new java.io.DataOutputStream(new java.io.FileOutputStream(target))) {
            data.writeInt(0x50445031);
            data.writeBytes("10");
            data.writeShort(1);
            data.writeUTF("example.bank");
            data.writeByte(2);
            data.writeByte(1);
        }
        ProtectionOptions previous = new ToggleFileStore(target).read();
        assertFalse(previous.notificationCenter);
        assertEquals(new AppRule(2, 1, false), previous.appRules.get("example.bank"));
        assertTrue(new ToggleFileStore(target).save(previous.withRule("example.bank", new AppRule(2, 1, true))));
        assertTrue(new ToggleFileStore(target).read().maximumFor("example.bank"));
    }

    @Test public void malformedMaximumFlagIsRejected() throws Exception {
        File target = new File(temporary.getRoot(), "invalid-maximum");
        ToggleFileStore store = new ToggleFileStore(target);
        assertTrue(store.save(ProtectionOptions.BOTH.withRule("example.bank", new AppRule(0, 0, true))));
        byte[] bytes = Files.readAllBytes(target.toPath());
        bytes[bytes.length - 1] = '2';
        Files.write(target.toPath(), bytes);
        try { store.read(); fail("Invalid Maximum value accepted"); } catch (IOException expected) { }
    }

    @Test public void appRulesAndGlobalsSurviveOneAtomicRoundTrip() throws Exception {
        File target = new File(temporary.getRoot(), "rules");
        ProtectionOptions options = new ProtectionOptions(false, true)
                .withRule("example.bank", new AppRule(2, 2)).withRule("example.chat", new AppRule(1, 0));
        assertTrue(new ToggleFileStore(target).save(options));
        assertTrue(new ToggleFileStore(target).read().sameAs(options));
    }

    @Test public void panelChoicesAndRulesSurvivePdp4RoundTrip() throws Exception {
        File target = new File(temporary.getRoot(), "panels");
        ProtectionOptions options = ProtectionOptions.NONE.withPanelPrivacy(true, false, true, true)
                .withRule("example.bank", new AppRule(1, 2, true));
        ToggleFileStore store = new ToggleFileStore(target);
        assertTrue(store.save(options));
        byte[] bytes = Files.readAllBytes(target.toPath());
        assertEquals(0x50445034, new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(bytes)).readInt());
        assertTrue(store.read().sameAs(options));
    }

    @Test public void failedAtomicWritePreservesAllSixBaseValuesAndRules() throws Exception {
        File target = new File(temporary.getRoot(), "rules");
        ProtectionOptions original = new ProtectionOptions(true, false)
                .withPanelPrivacy(true, false, true, false)
                .withRule("example.bank", new AppRule(2, 2, true));
        assertTrue(new ToggleFileStore(target).save(original));
        ToggleFileStore failing = new ToggleFileStore(target) {
            @Override void replace(Path source, Path destination) throws IOException { throw new IOException(); }
        };
        ProtectionOptions replacement = new ProtectionOptions(false, true)
                .withPanelPrivacy(false, true, false, true)
                .withRule("example.chat", new AppRule(1, 1));
        assertFalse(failing.save(replacement));
        assertTrue(new ToggleFileStore(target).read().sameAs(original));
    }

    @Test public void truncatedAndTrailingRuleDataAreRejected() throws Exception {
        File target = new File(temporary.getRoot(), "rules");
        ToggleFileStore store = new ToggleFileStore(target);
        assertTrue(store.save(ProtectionOptions.BOTH.withRule("example.bank", new AppRule(2, 2))));
        byte[] valid = Files.readAllBytes(target.toPath());
        for (int length : new int[]{3, 5, valid.length - 1, valid.length + 1}) {
            Files.write(target.toPath(), java.util.Arrays.copyOf(valid, length));
            try { store.read(); fail("Malformed rules accepted"); } catch (IOException expected) { }
        }
    }

    @Test public void allSixteenPanelCombinationsSurviveReopeningIndependently() throws Exception {
        File target = new File(temporary.getRoot(), "panels");
        for (int flags = 0; flags < 16; flags++) {
            ProtectionOptions value = ProtectionOptions.NONE.withPanelPrivacy(
                    (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
            assertTrue(new ToggleFileStore(target).save(value));
            assertTrue("panel combination " + flags, new ToggleFileStore(target).read().sameAs(value));
        }
    }

    @Test public void legacyEnabledAndDisabledValuesMigrateWithoutLosingUserChoice() throws Exception {
        File target = temporary.newFile("legacy");
        Files.write(target.toPath(), new byte[]{'1'});
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.BOTH));
        Files.write(target.toPath(), new byte[]{'0'});
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.NONE));
        Files.write(target.toPath(), new byte[]{'0', '1'});
        ProtectionOptions oldPair = new ToggleFileStore(target).read();
        assertTrue(oldPair.sameAs(new ProtectionOptions(false, true)));
        assertFalse(oldPair.notificationCenter);
        assertTrue(new ToggleFileStore(target).save(oldPair));
        assertTrue(new ToggleFileStore(target).read().sameAs(oldPair));
    }

    @Test public void malformedLegacyAndPartialPairsAreRejected() throws Exception {
        File target = temporary.newFile("invalid");
        for (byte[] bytes : new byte[][]{{}, {'x'}, {'1', 'x'}, {'x', '1'}, {'1', '0', '1'}}) {
            Files.write(target.toPath(), bytes);
            try { new ToggleFileStore(target).read(); fail("Expected invalid configuration"); }
            catch (IOException expected) { }
        }
    }

    @Test public void newInstallDefaultsOnAndSavedChoiceSurvivesNewInstance() throws Exception {
        File target = new File(temporary.getRoot(), "toggle");
        ToggleFileStore store = new ToggleFileStore(target);
        assertTrue(store.read().sameAs(ProtectionOptions.BOTH));
        assertTrue(store.save(ProtectionOptions.NONE));
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.NONE));
        assertTrue(store.save(ProtectionOptions.BOTH));
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.BOTH));
    }

    @Test public void oldPdp2RulesKeepMaximumAndDisableAllPanels() throws Exception {
        File target = new File(temporary.getRoot(), "old-pdp2");
        try (var data = new java.io.DataOutputStream(new java.io.FileOutputStream(target))) {
            data.writeInt(0x50445032);
            data.writeBytes("10");
            data.writeShort(1);
            data.writeUTF("example.bank");
            data.writeByte(2);
            data.writeByte(1);
            data.writeByte('1');
        }
        ProtectionOptions previous = new ToggleFileStore(target).read();
        assertPanelsOff(previous);
        assertEquals(new AppRule(2, 1, true), previous.appRules.get("example.bank"));
    }

    @Test public void pdp3MigrationKeepsNotificationCenterAndMaximumRulesOnly() throws Exception {
        File target = new File(temporary.getRoot(), "old-pdp3");
        try (var data = new java.io.DataOutputStream(new java.io.FileOutputStream(target))) {
            data.writeInt(0x50445033);
            data.writeBytes("101");
            data.writeShort(1);
            data.writeUTF("example.bank");
            data.writeByte(2);
            data.writeByte(1);
            data.writeByte('1');
        }
        ProtectionOptions migrated = new ToggleFileStore(target).read();
        assertTrue(migrated.notificationCenter);
        assertFalse(migrated.quickSettings);
        assertFalse(migrated.notificationCenterMaximum);
        assertFalse(migrated.quickSettingsMaximum);
        assertEquals(new AppRule(2, 1, true), migrated.appRules.get("example.bank"));
    }

    @Test public void malformedPdp4FlagAndTruncationAreRejected() throws Exception {
        File target = new File(temporary.getRoot(), "invalid-pdp4");
        ToggleFileStore store = new ToggleFileStore(target);
        assertTrue(store.save(ProtectionOptions.NONE.withPanelPrivacy(true, true, true, true)));
        byte[] valid = Files.readAllBytes(target.toPath());
        for (int index = 6; index <= 9; index++) {
            byte[] invalidFlag = valid.clone();
            invalidFlag[index] = '2';
            Files.write(target.toPath(), invalidFlag);
            try { store.read(); fail("Invalid PDP4 panel flag accepted"); } catch (IOException expected) { }
        }
        Files.write(target.toPath(), java.util.Arrays.copyOf(valid, 8));
        try { store.read(); fail("Truncated PDP4 data accepted"); } catch (IOException expected) { }
    }

    @Test public void failedFirstWritePreservesDefaultAndCreatesNoConfiguration() throws Exception {
        File target = new File(temporary.getRoot(), "toggle");
        ToggleFileStore failing = new ToggleFileStore(target) {
            @Override void replace(Path source, Path destination) throws IOException { throw new IOException(); }
        };
        assertFalse(failing.save(ProtectionOptions.NONE));
        assertFalse(failing.exists());
        assertTrue(failing.read().sameAs(ProtectionOptions.BOTH));
    }

    @Test(expected = IOException.class) public void malformedFileIsRejected() throws Exception {
        File target = temporary.newFile("toggle");
        Files.write(target.toPath(), new byte[]{'x', 'x'});
        new ToggleFileStore(target).read();
    }

    private static void assertPanelsOff(ProtectionOptions options) {
        assertFalse(options.notificationCenter);
        assertFalse(options.quickSettings);
        assertFalse(options.notificationCenterMaximum);
        assertFalse(options.quickSettingsMaximum);
    }
}
