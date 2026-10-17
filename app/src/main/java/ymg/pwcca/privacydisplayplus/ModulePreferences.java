package ymg.pwcca.privacydisplayplus;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.IOException;

final class ModulePreferences {
    private ModulePreferences() {}
    private static SharedPreferences preferences(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("popup_control", Context.MODE_PRIVATE);
    }
    private static ToggleFileStore storage(Context context) {
        Context device = context.createDeviceProtectedStorageContext();
        return new ToggleFileStore(new File(device.getFilesDir(), "popup-enabled"));
    }
    static synchronized ProtectionOptions options(Context context) {
        ToggleFileStore store = storage(context);
        if (!store.exists()) {
            // Migrate the previously installed UI's preference without mutating its cache.
            boolean previous = preferences(context).getBoolean(ControlProtocol.ENABLED, true);
            ProtectionOptions migrated = new ProtectionOptions(previous, previous);
            store.save(migrated);
            return migrated;
        }
        try { return store.read(); }
        catch (IOException failure) { return ProtectionOptions.NONE; }
    }
    static synchronized boolean save(Context context, ProtectionOptions options) {
        return storage(context).save(options);
    }
    static synchronized ProtectionOptions update(Context context,
            java.util.function.UnaryOperator<ProtectionOptions> change) {
        ProtectionOptions desired = change.apply(options(context));
        return storage(context).save(desired) ? desired : null;
    }
}
