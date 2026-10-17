package ymg.pwcca.privacydisplayplus;

/** Fixed privileged action; paths come only from PackageManager, never Intent/user input. */
final class SoftRebootCommand {
    static final String MARKER = "__PDPLUS_SOFT_REBOOT__";
    private SoftRebootCommand() {}

    static String quote(String value) {
        if (value == null || !value.startsWith("/")) throw new IllegalArgumentException("Absolute path required");
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new IllegalArgumentException("Invalid path");
        return "'" + value.replace("'", "'\\''") + "'";
    }

    static String script(String managerDaemon) {
        String candidates = "'/data/adb/ksud' '/data/adb/ksu/bin/ksud'";
        if (managerDaemon != null) candidates += " " + quote(managerDaemon);
        return "[ \"$(id -u)\" = 0 ] || exit 71\n"
                + "for daemon in " + candidates + "; do\n"
                + "  [ -x \"$daemon\" ] || continue\n"
                + "  if \"$daemon\" --help 2>/dev/null | grep -q 'soft-reboot'; then\n"
                + "    echo " + MARKER + "\n"
                + "    exec \"$daemon\" soft-reboot\n"
                + "  fi\n"
                + "done\nexit 72\n";
    }
}
