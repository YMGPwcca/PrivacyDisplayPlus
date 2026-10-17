package ymg.pwcca.privacydisplayplus;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Read-only boot configuration; no root command or mutation is exposed through IPC. */
public final class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String argument, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID && uid != Process.myUid()) {
            throw new SecurityException("Configuration is private to the app and system server");
        }
        if (!ControlProtocol.GET_CONFIG.equals(method)) throw new IllegalArgumentException("Unknown call");
        Bundle result = new Bundle();
        ControlProtocol.writeOptions(result, ModulePreferences.options(requireContext()));
        return result;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
