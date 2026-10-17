package ymg.pwcca.privacydisplayplus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Runs one explicit action off the UI thread; never retries an action after it started. */
final class RootCommandRunner {
    enum Result { REQUESTED, ROOT_UNAVAILABLE, DENIED, UNSUPPORTED, FAILED, TIMED_OUT, ALREADY_RUNNING }
    private static final RootOperationState STATE = new RootOperationState();
    static RootOperationState state() { return STATE; }

    Result softReboot(String managerDaemon) {
        if (!STATE.begin()) return Result.ALREADY_RUNNING;
        Result result = runAction(managerDaemon);
        STATE.finish(result);
        return result;
    }

    private Result runAction(String managerDaemon) {
        Process process = null;
        try {
            process = launch(managerDaemon);
            return execute(process, SoftRebootCommand.script(managerDaemon));
        } catch (IOException failure) {
            return Result.ROOT_UNAVAILABLE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        } catch (RuntimeException failure) {
            return Result.FAILED;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private Process launch(String managerDaemon) throws IOException {
        if (managerDaemon != null) {
            try {
                return new ProcessBuilder(managerDaemon, "debug", "su", "-g")
                        .redirectErrorStream(true).start();
            } catch (IOException ignored) { /* No command was started; try the standard su entry. */ }
        }
        return new ProcessBuilder("su", "-mm").redirectErrorStream(true).start();
    }

    Result execute(Process process, String script) throws IOException, InterruptedException {
        OutputCapture capture = new OutputCapture(process.getInputStream());
        Thread reader = new Thread(capture, "popup-root-output");
        reader.setDaemon(true);
        reader.start();
        process.getOutputStream().write(script.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        if (!process.waitFor(60, TimeUnit.SECONDS)) return Result.TIMED_OUT;
        reader.join(1000);
        return classify(process.exitValue(), capture.sawMarker());
    }

    static Result classify(int exit, boolean marker) {
        if (exit == 71) return Result.DENIED;
        if (exit == 72) return Result.UNSUPPORTED;
        if (!marker) return Result.DENIED;
        return exit == 0 ? Result.REQUESTED : Result.FAILED;
    }

    private static final class OutputCapture implements Runnable {
        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        OutputCapture(InputStream input) { this.input = input; }
        @Override public void run() {
            byte[] buffer = new byte[512];
            try (InputStream stream = input) {
                int count;
                while ((count = stream.read(buffer)) != -1) append(buffer, count);
            } catch (IOException ignored) { /* The process may disappear during userspace restart. */ }
        }
        private synchronized void append(byte[] bytes, int count) {
            int remaining = 8192 - captured.size();
            if (remaining > 0) captured.write(bytes, 0, Math.min(remaining, count));
        }
        synchronized boolean sawMarker() {
            return captured.toString(StandardCharsets.UTF_8).contains(SoftRebootCommand.MARKER);
        }
    }
}
