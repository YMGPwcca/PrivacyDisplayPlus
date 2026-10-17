package ymg.pwcca.privacydisplayplus;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class SoftRebootCommandTest {
    @Test public void onlySupportedDaemonActionCanExecute() {
        String script = SoftRebootCommand.script("/data/app/manager/lib/arm64/libksud.so");
        assertTrue(script.contains("[ \"$(id -u)\" = 0 ] || exit 71"));
        assertTrue(script.contains("--help 2>/dev/null | grep -q 'soft-reboot'"));
        assertTrue(script.contains("exec \"$daemon\" soft-reboot"));
        assertFalse(script.contains("svc power"));
        assertFalse(script.contains("setprop"));
        assertFalse(script.contains("/system/bin/reboot"));
        assertFalse(script.contains("kill "));
    }

    @Test public void managerPathCannotInjectShellCommands() {
        String path = "/data/app/a'$(id);x/libksud.so";
        assertEquals("'/data/app/a'\\''$(id);x/libksud.so'", SoftRebootCommand.quote(path));
    }

    @Test(expected = IllegalArgumentException.class) public void relativeDaemonPathIsRejected() {
        SoftRebootCommand.script("libksud.so");
    }

    @Test(expected = IllegalArgumentException.class) public void multilineDaemonPathIsRejected() {
        SoftRebootCommand.script("/path\nreboot");
    }

    @Test public void noManagerStillUsesOnlyKnownDaemonPaths() {
        assertTrue(SoftRebootCommand.script(null).contains("'/data/adb/ksud' '/data/adb/ksu/bin/ksud'"));
    }

    @Test public void successfulShellWithoutActionMarkerIsNotReportedAsRestart() {
        assertEquals(RootCommandRunner.Result.DENIED, RootCommandRunner.classify(0, false));
        assertEquals(RootCommandRunner.Result.REQUESTED, RootCommandRunner.classify(0, true));
        assertEquals(RootCommandRunner.Result.UNSUPPORTED, RootCommandRunner.classify(72, false));
        assertEquals(RootCommandRunner.Result.DENIED, RootCommandRunner.classify(71, false));
        assertEquals(RootCommandRunner.Result.FAILED, RootCommandRunner.classify(1, true));
    }

    @Test public void runnerWritesExactScriptAndRecognizesAcceptedAction() throws Exception {
        FakeProcess process = new FakeProcess(0, SoftRebootCommand.MARKER, true);
        String script = SoftRebootCommand.script(null);
        assertEquals(RootCommandRunner.Result.REQUESTED, new RootCommandRunner().execute(process, script));
        assertEquals(script, process.input.toString(StandardCharsets.UTF_8));
    }

    @Test public void runnerTimesOutWithoutSendingAnotherCommand() throws Exception {
        FakeProcess process = new FakeProcess(0, "", false);
        String script = SoftRebootCommand.script(null);
        assertEquals(RootCommandRunner.Result.TIMED_OUT, new RootCommandRunner().execute(process, script));
        assertEquals(script, process.input.toString(StandardCharsets.UTF_8));
    }

    private static final class FakeProcess extends Process {
        final ByteArrayOutputStream input = new ByteArrayOutputStream();
        final int exit;
        final String output;
        final boolean completes;
        FakeProcess(int exit, String output, boolean completes) {
            this.exit = exit; this.output = output; this.completes = completes;
        }
        @Override public OutputStream getOutputStream() { return input; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return completes; }
        @Override public int exitValue() { return exit; }
        @Override public void destroy() { }
    }
}
