package io.awarebridge.android;

import android.content.Context;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Calls this project's public ROM extension; never reads NIK, NPK or pairing files. */
final class NanIdentityClient implements AutoCloseable {
    interface Callback { void done(List<Identity> records, String source, String error); }
    static final class Identity {
        final String alias;
        final String peerId;
        Identity(String alias, String peerId) {
            this.alias = alias;
            this.peerId = peerId;
        }
    }
    private final WifiAwareManager manager;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean started, closed;
    private final Runnable timeout = () -> finish(null, null, "读取配对身份超时，请重试");

    NanIdentityClient(Context context, Callback callback) {
        manager = context.getApplicationContext().getSystemService(WifiAwareManager.class);
        this.callback = callback;
    }
    void read() {
        if (closed || started) return;
        started = true;
        main.postDelayed(timeout, 10_000);
        worker.execute(() -> {
            try {
                if (manager == null) throw new UnsupportedOperationException();
                // Public API on the patched ROM. Reflection keeps stock SDK 37 builds working.
                // The ROM must publish it in framework/api/current.txt, not just add @hide.
                Bundle response = (Bundle) WifiAwareManager.class
                        .getMethod("getAwareBridgePairingIdentities").invoke(manager);
                if (response == null || !"ok".equals(response.getString("status"))
                        || !"framework-memory".equals(response.getString("source"))) {
                    throw new IllegalArgumentException();
                }
                ArrayList<Bundle> data = response.getParcelableArrayList("records", Bundle.class);
                if (data == null || data.size() > 256) throw new IllegalArgumentException();
                List<Identity> records = new ArrayList<>();
                for (Bundle b : data) {
                    if (b == null) throw new IllegalArgumentException();
                    String alias = b.getString("alias"), peerId = b.getString("peerId");
                    if (alias == null || alias.isEmpty() || peerId == null
                            || !peerId.matches("[0-9a-f]{64}")) throw new IllegalArgumentException();
                    records.add(new Identity(alias, peerId));
                }
                main.post(() -> finish(records, "framework-memory", null));
            } catch (NoSuchMethodException | UnsupportedOperationException e) {
                main.post(() -> finish(null, null, "当前 ROM 尚未提供配对身份接口；普通配对缓存管理仍可使用"));
            } catch (InvocationTargetException e) {
                String message = e.getCause() instanceof SecurityException
                        ? "读取被系统拒绝，请检查附近设备及 Wi-Fi 权限"
                        : "系统读取配对身份失败，请重试";
                main.post(() -> finish(null, null, message));
            } catch (Exception | LinkageError e) {
                main.post(() -> finish(null, null, "配对身份接口不可用，请核对 ROM 接口版本"));
            }
        });
    }
    private void finish(List<Identity> records, String source, String error) {
        if (closed) return;
        close();
        callback.done(records, source, error);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        main.removeCallbacks(timeout);
        worker.shutdownNow();
    }
}
