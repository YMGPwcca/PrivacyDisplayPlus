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

    @Test public void appRulesAndGlobalsSurviveOneAtomicRoundTrip() throws Exception {
        File target = new File(temporary.getRoot(), "rules");
        ProtectionOptions options = new ProtectionOptions(false, true)
                .withRule("example.bank", new AppRule(2, 2)).withRule("example.chat", new AppRule(1, 0));
        assertTrue(new ToggleFileStore(target).save(options));
        assertTrue(new ToggleFileStore(target).read().sameAs(options));
    }

    @Test public void failedRuleWriteKeepsTheEntirePreviousConfiguration() throws Exception {
        File target = new File(temporary.getRoot(), "rules");
        ProtectionOptions original = ProtectionOptions.BOTH.withRule("example.bank", new AppRule(2, 2));
        assertTrue(new ToggleFileStore(target).save(original));
        ToggleFileStore failing = new ToggleFileStore(target) {
            @Override void replace(Path source, Path destination) throws IOException { throw new IOException(); }
        };
        assertFalse(failing.save(ProtectionOptions.NONE.withRule("example.chat", new AppRule(1, 1))));
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

    @Test public void allIndependentChoicesSurviveReopeningTheStore() throws Exception {
        File target = new File(temporary.getRoot(), "toggle");
        for (boolean floating : new boolean[]{false, true}) {
            for (boolean split : new boolean[]{false, true}) {
                ProtectionOptions value = new ProtectionOptions(floating, split);
                assertTrue(new ToggleFileStore(target).save(value));
                assertTrue(new ToggleFileStore(target).read().sameAs(value));
            }
        }
    }

    @Test public void legacyEnabledAndDisabledValuesMigrateWithoutLosingUserChoice() throws Exception {
        File target = temporary.newFile("legacy");
        Files.write(target.toPath(), new byte[]{'1'});
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.BOTH));
        Files.write(target.toPath(), new byte[]{'0'});
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.NONE));
        ProtectionOptions onlySplit = new ProtectionOptions(false, true);
        assertTrue(new ToggleFileStore(target).save(onlySplit));
        assertEquals(2, Files.size(target.toPath()));
        assertTrue(new ToggleFileStore(target).read().sameAs(onlySplit));
    }

    @Test public void failedReplaceNeverCommitsHalfOfThePreferencePair() throws Exception {
        File target = new File(temporary.getRoot(), "toggle");
        ProtectionOptions previous = new ProtectionOptions(true, false);
        assertTrue(new ToggleFileStore(target).save(previous));
        ToggleFileStore failing = new ToggleFileStore(target) {
            @Override void replace(Path source, Path destination) throws IOException {
                throw new IOException("Injected rename failure");
            }
        };
        assertFalse(failing.save(new ProtectionOptions(false, true)));
        assertTrue(new ToggleFileStore(target).read().sameAs(previous));
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

    @Test public void failedAtomicReplaceCannotExposeUnsavedChoice() throws Exception {
        File target = new File(temporary.getRoot(), "toggle");
        assertTrue(new ToggleFileStore(target).save(ProtectionOptions.BOTH));
        ToggleFileStore failing = new ToggleFileStore(target) {
            @Override void replace(Path source, Path destination) throws IOException {
                throw new IOException("Injected rename failure");
            }
        };
        assertFalse(failing.save(ProtectionOptions.NONE));
        assertTrue(failing.read().sameAs(ProtectionOptions.BOTH));
        assertTrue(new ToggleFileStore(target).read().sameAs(ProtectionOptions.BOTH));
        assertFalse(new File(target.getPath() + ".new").exists());
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
}
