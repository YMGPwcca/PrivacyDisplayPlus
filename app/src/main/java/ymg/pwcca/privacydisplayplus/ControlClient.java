package ymg.pwcca.privacydisplayplus;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

final class ControlClient {
    interface Callback { void accept(boolean acknowledged, Bundle state); }
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int generation;

    ControlClient(Context context) { this.context = context.getApplicationContext(); }

    void query(Callback callback) { request(ControlProtocol.QUERY, null, callback); }
    void set(ProtectionOptions options, Callback callback) {
        Bundle values = new Bundle();
        ControlProtocol.writeOptions(values, options);
        request(ControlProtocol.SET, values, callback);
    }
    void apps(Callback callback) { request(ControlProtocol.APPS, null, callback); }
    void close() { generation++; handler.removeCallbacksAndMessages(null); }

    private void request(String action, Bundle values, Callback callback) {
        int request = ++generation;
        Intent intent = new Intent(action)
                .setPackage("android").addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(ControlProtocol.VERSION, ControlProtocol.VERSION_CODE);
        if (values != null) intent.putExtras(values);
        Runnable timeout = () -> finish(request, false, null, callback);
        handler.postDelayed(timeout, 3000);
        try {
            context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent result) {
                    handler.removeCallbacks(timeout);
                    finish(request, getResultCode() == Activity.RESULT_OK, getResultExtras(false), callback);
                }
            }, handler, Activity.RESULT_CANCELED, null, null);
        } catch (RuntimeException failure) {
            handler.removeCallbacks(timeout);
            finish(request, false, null, callback);
        }
    }

    private void finish(int request, boolean acknowledged, Bundle state, Callback callback) {
        if (request != generation) return;
        generation++;
        callback.accept(acknowledged, state);
    }
}
