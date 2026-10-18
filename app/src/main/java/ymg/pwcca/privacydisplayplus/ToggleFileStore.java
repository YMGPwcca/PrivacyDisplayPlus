package ymg.pwcca.privacydisplayplus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.TreeMap;

/** No mutable preference cache: only a completed atomic replacement changes the value. */
class ToggleFileStore {
    private final File target;
    ToggleFileStore(File target) { this.target = target; }
    boolean exists() { return target.isFile(); }

    ProtectionOptions read() throws IOException {
        if (!exists()) return ProtectionOptions.BOTH;
        if (Files.size(target.toPath()) > 131072) throw new IOException("Protection storage too large");
        byte[] bytes = Files.readAllBytes(target.toPath());
        if (bytes.length == 1) {
            // v0.5 and earlier used one switch for both window types.
            boolean previous = decode(bytes[0]);
            return new ProtectionOptions(previous, previous);
        }
        if (bytes.length == 2) return new ProtectionOptions(decode(bytes[0]), decode(bytes[1]));
        return readRules(bytes);
    }

    private static ProtectionOptions readRules(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int format = input.readInt();
            if (format != 0x50445031 && format != 0x50445032 && format != 0x50445033
                    && format != 0x50445034) {
                throw new IOException("Invalid protection storage");
            }
            boolean floating = decode(input.readByte()), split = decode(input.readByte());
            boolean notificationCenter = false, quickSettings = false;
            boolean notificationCenterMaximum = false, quickSettingsMaximum = false;
            if (format == 0x50445033) {
                notificationCenter = decode(input.readByte());
            } else if (format == 0x50445034) {
                notificationCenter = decode(input.readByte());
                quickSettings = decode(input.readByte());
                notificationCenterMaximum = decode(input.readByte());
                quickSettingsMaximum = decode(input.readByte());
            }
            int count = input.readUnsignedShort();
            if (count > 256) throw new IOException("Too many app rules");
            TreeMap<String, AppRule> rules = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                String name = input.readUTF();
                int floatingRule = input.readUnsignedByte(), splitRule = input.readUnsignedByte();
                boolean maximum = format != 0x50445031 && decode(input.readByte());
                AppRule rule = new AppRule(floatingRule, splitRule, maximum);
                if (rules.put(name, rule) != null) throw new IOException("Duplicate app rule");
            }
            if (input.available() != 0) throw new IOException("Trailing protection data");
            return new ProtectionOptions(floating, split, notificationCenter, quickSettings,
                    notificationCenterMaximum, quickSettingsMaximum, rules);
        } catch (IllegalArgumentException failure) { throw new IOException("Invalid app rule", failure); }
    }

    private static boolean decode(byte value) throws IOException {
        if (value == '1') return true;
        if (value == '0') return false;
        throw new IOException("Invalid protection value");
    }

    boolean save(ProtectionOptions value) {
        Path temporary = target.toPath().resolveSibling(target.getName() + ".new");
        try {
            write(temporary.toFile(), value);
            replace(temporary, target.toPath());
            return true;
        } catch (IOException failure) {
            return false;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) { /* A failed temporary write is never read as configuration. */ }
        }
    }

    private void write(File temporary, ProtectionOptions value) throws IOException {
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            DataOutputStream data = new DataOutputStream(output);
            data.writeInt(0x50445034);
            output.write(value.floating ? '1' : '0');
            output.write(value.split ? '1' : '0');
            output.write(value.notificationCenter ? '1' : '0');
            output.write(value.quickSettings ? '1' : '0');
            output.write(value.notificationCenterMaximum ? '1' : '0');
            output.write(value.quickSettingsMaximum ? '1' : '0');
            data.writeShort(value.appRules.size());
            for (var entry : value.appRules.entrySet()) {
                data.writeUTF(entry.getKey());
                data.writeByte(entry.getValue().floating());
                data.writeByte(entry.getValue().split());
                data.writeByte(entry.getValue().maximum() ? '1' : '0');
            }
            data.flush();
            output.getFD().sync();
        }
    }

    void replace(Path temporary, Path destination) throws IOException {
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
