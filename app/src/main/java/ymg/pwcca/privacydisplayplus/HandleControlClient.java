package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;

/** SystemUI can only query/change the supplied native popup token, not app-wide settings. */
final class HandleControlClient {
    interface Callback { void accept(boolean ok, Bundle state); }
    private final Context context;
    private final Handler handler;
    private int generation;

    HandleControlClient(Context context, Looper viewLooper) {
        this.context = context.getApplicationContext();
        handler = new Handler(java.util.Objects.requireNonNull(viewLooper, "Missing popup owner Looper"));
    }
    void query(Parcelable token, Callback callback) { request(token, null, callback); }
    void set(Parcelable token, boolean enabled, Callback callback) { request(token, enabled, callback); }
    void close() { generation++; handler.removeCallbacksAndMessages(null); }

    private void request(Parcelable token, Boolean enabled, Callback callback) {
        int request = ++generation;
        Intent intent = new Intent(enabled == null ? ControlProtocol.HANDLE_QUERY : ControlProtocol.HANDLE_SET)
                .setPackage("android").addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE)
                .putExtra(ControlProtocol.TASK_TOKEN, token);
        if (enabled != null) intent.putExtra(ControlProtocol.PROTECTED, enabled);
        Runnable timeout = () -> complete(request, false, null, callback);
        handler.postDelayed(timeout, 3000);
        try {
            Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
            context.sendOrderedBroadcast(intent, null, options, new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent received) {
                    handler.removeCallbacks(timeout);
                    Bundle reply = getResultExtras(false);
                    boolean valid = getResultCode() == Activity.RESULT_OK && reply != null
                            && reply.getInt(ControlProtocol.VERSION) == ControlProtocol.VERSION_CODE;
                    complete(request, valid, reply, callback);
                }
            }, handler, Activity.RESULT_CANCELED, null, null);
        } catch (RuntimeException failure) {
            handler.removeCallbacks(timeout);
            complete(request, false, null, callback);
        }
    }

    private void complete(int request, boolean ok, Bundle state, Callback callback) {
        if (request != generation) return;
        generation++;
        try { callback.accept(ok, state); }
        catch (RuntimeException failure) {
            android.util.Log.w("PrivacyDisplay+", "Handle callback stopped ("
                    + failure.getClass().getSimpleName() + ")");
        }
    }
}
