package io.awarebridge.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Manages only the calling app's system pairing aliases, never pairing keys. */
final class PairingCacheDialog {
    private final Activity activity;
    private final WifiAwareManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AlertDialog dialog;
    private final LinearLayout rows;
    private final TextView status;
    private AlertDialog confirmation;
    private int query;
    private boolean closed;

    PairingCacheDialog(Activity activity) {
        this.activity = activity;
        manager = activity.getSystemService(WifiAwareManager.class);
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        rows.setPadding(pad, pad, pad, pad);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(activity);
        status.setPadding(pad, pad, pad, 0);
        content.addView(status);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(rows);
        content.addView(scroll);
        dialog = new AlertDialog.Builder(activity)
                .setTitle("配对缓存")
                .setView(content)
                .setNegativeButton("关闭", null)
                .setNeutralButton("刷新", null)
                .setPositiveButton("清空全部", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            closed = true;
            query++;
            handler.removeCallbacksAndMessages(null);
            if (confirmation != null) confirmation.dismiss();
        });
    }

    void show() {
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> refresh(null));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> confirmRemoval(null));
        refresh(null);
    }

    void dismiss() { dialog.dismiss(); }

    private void refresh(String result) {
        if (closed) return;
        final int token = ++query;
        rows.removeAllViews();
        status.setText("正在获取本应用的配对缓存…");
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        Runnable timeout = () -> {
            if (!closed && query == token) {
                query++;
                error("获取超时，请重试");
            }
        };
        handler.postDelayed(timeout, 10_000);
        try {
            if (manager == null) throw new IllegalStateException("设备不支持 Wi-Fi Aware");
            manager.getPairedDevices(activity.getMainExecutor(), aliases -> {
                if (closed || query != token) return;
                handler.removeCallbacks(timeout);
                List<String> sorted = new ArrayList<>(aliases);
                Collections.sort(sorted);
                status.setText((result == null ? "" : result + "\n")
                        + (sorted.isEmpty() ? "本应用没有配对缓存" : "本应用的配对缓存：" + sorted.size() + " 条")
                        + "\n以下为系统配对别名；删除后需重新配对。iPhone 端记录需在系统设置中单独删除。");
                for (String alias : sorted) {
                    Button remove = new Button(activity);
                    remove.setAllCaps(false);
                    remove.setText("删除：" + alias);
                    remove.setOnClickListener(v -> confirmRemoval(alias));
                    rows.addView(remove);
                }
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!sorted.isEmpty());
            });
        } catch (RuntimeException | LinkageError e) {
            handler.removeCallbacks(timeout);
            error("获取失败：" + e.getMessage());
        }
    }

    private void confirmRemoval(String alias) {
        confirmation = new AlertDialog.Builder(activity)
                .setTitle(alias == null ? "清空本应用的全部配对缓存？" : "删除这条配对缓存？")
                .setMessage(alias == null ? "本应用的所有已配对设备都需要重新配对。" : alias + "\n删除后需要重新配对。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (ignored, which) -> {
                    try {
                        if (alias == null) manager.resetPairedDevices();
                        else manager.removePairedDevice(alias);
                        refresh("已提交删除请求，重新查询结果如下");
                    } catch (RuntimeException | LinkageError e) {
                        error("删除失败：" + e.getMessage());
                    }
                }).show();
    }

    private void error(String message) {
        if (closed) return;
        status.setText(message);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }
}
