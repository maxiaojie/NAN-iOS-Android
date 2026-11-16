package io.awarebridge.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** A foreground demo: no background timers or implicit screen-off guarantees. */
public final class MainActivity extends Activity {
    private TextView state, pin, stats, peer, lastMessage;
    private Button toggle;
    private Switch pairingSwitch;
    private boolean syncingPairing;
    private AwareController controller;
    private static final String[] PERMISSIONS = {
            Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(0xfff5f7fb);
        TextView title = label(content, "AwareBridge", 30);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        label(content, "Wi-Fi Aware · iOS ↔ Android", 16);
        state = label(content, "准备就绪", 20);
        pin = label(content, "", 32);
        toggle = new Button(this); toggle.setText("开始发布"); content.addView(toggle);
        pairingSwitch = new Switch(this);
        pairingSwitch.setText("允许新设备配对（1 分钟）");
        pairingSwitch.setEnabled(false);
        content.addView(pairingSwitch);
        pairingSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!syncingPairing && controller != null) controller.setPairingEnabled(checked);
        });
        peer = label(content, "对端：—", 14);
        stats = label(content, "发送 0  ·  接收 0", 20);
        lastMessage = label(content, "每 5 秒双向心跳，收到后回复确认", 16);
        label(content, "1. 点击开始发布\n2. 打开允许配对，iOS 添加设备并输入代码\n3. iOS 点击连接\n\n请保持两端 App 在前台。", 15);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        toggle.setOnClickListener(v -> {
            if (controller != null) { stop("已停止"); return; }
            for (String p : PERMISSIONS) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(PERMISSIONS, 1); return;
                }
            }
            start();
        });
    }
    private TextView label(LinearLayout parent, String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(0xff202c40); text.setPadding(0, 12, 0, 12); parent.addView(text);
        return text;
    }
    private void start() {
        if (controller != null) return;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            state.setText("设备不支持 Wi-Fi Aware"); return;
        }
        if (!getSystemService(LocationManager.class).isLocationEnabled()) {
            state.setText("无法开始发布：系统定位服务未开启");
            new AlertDialog.Builder(this)
                    .setTitle("无法开始发布")
                    .setMessage("系统定位服务未开启。请打开定位服务，返回应用后再次点击“开始发布”。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("打开定位设置", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        } catch (ActivityNotFoundException e) {
                            state.setText("无法打开定位设置，请手动前往系统设置开启定位服务");
                            Toast.makeText(this, "请手动前往系统设置开启定位服务", Toast.LENGTH_LONG).show();
                        }
                    }).show();
            return;
        }
        peer.setText("对端：—"); stats.setText("发送 0  ·  接收 0");
        lastMessage.setText("等待心跳…");
        controller = new AwareController(this, new AwareController.Observer() {
            @Override public void status(String value) { state.setText(value); }
            @Override public void pin(String value) { pin.setText(value.isEmpty() ? "" : "配对代码\n" + value); }
            @Override public void stats(long tx, long rx, long ageMs, String remote, String text) {
                stats.setText(String.format(Locale.ROOT, "发送 %d  ·  接收 %d", tx, rx));
                peer.setText("对端：" + remote);
                lastMessage.setText(text + (ageMs < 0 ? "" : "\n最近接收：" + ageMs / 1000 + " 秒前"));
                if (rx > 0) state.setText(ageMs > 20_000 ? "心跳超时，请检查 iOS 是否在前台" : "已连接 · 双向数据通信中");
            }
            @Override public void pairing(boolean enabled, boolean available) {
                syncingPairing = true;
                pairingSwitch.setChecked(enabled);
                pairingSwitch.setEnabled(available);
                syncingPairing = false;
            }
            @Override public void stopped() {
                controller = null; toggle.setText("重新发布");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });
        toggle.setText("停止");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        controller.start();
    }
    private void stop(String status) {
        if (controller != null) { controller.close(); controller = null; }
        toggle.setText("开始发布"); state.setText(status);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 1) return;
        for (String p : PERMISSIONS) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
            state.setText("需要附近设备和定位权限才能发现并连接"); return;
        }
        start();
    }
    @Override protected void onStop() { super.onStop(); stop("已暂停；回到前台后请重新发布"); }
    @Override protected void onDestroy() { stop("已停止"); super.onDestroy(); }
}
