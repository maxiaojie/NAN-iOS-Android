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
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Foreground publisher or subscriber. Role changes require stopping the session. */
public final class MainActivity extends Activity {
    private TextView state, pin, stats, peer, lastMessage, instructions, benchmarkStats;
    private Button toggle, pairButton, connectButton, cacheButton;
    private Switch pairingSwitch;
    private RadioGroup roles;
    private Spinner peerPicker;
    private LinearLayout subscriberControls;
    private AlertDialog pinDialog;
    private PairingCacheDialog cacheDialog;
    private boolean syncingPairing;
    private BridgeController controller;
    private final List<SubscriberController.Peer> discovered = new ArrayList<>();
    private static final int PUBLISHER = 1001, SUBSCRIBER = 1002;
    private static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_LOCAL_NETWORK,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        content.setBackgroundColor(0xfff5f7fb);
        label(content, "AwareBridge", 30).setTypeface(null, android.graphics.Typeface.BOLD);
        label(content, "Wi-Fi Aware · iOS ↔ Android", 16);
        roles = new RadioGroup(this);
        for (int id : new int[]{PUBLISHER, SUBSCRIBER}) {
            RadioButton choice = new RadioButton(this);
            choice.setId(id);
            choice.setText(id == PUBLISHER ? "发布 · iOS 连接 Android" : "订阅 · Android 连接 iOS");
            roles.addView(choice);
        }
        roles.check(PUBLISHER);
        content.addView(roles);
        state = label(content, "准备就绪", 20);
        pin = label(content, "", 32);
        toggle = new Button(this);
        content.addView(toggle);
        pairingSwitch = new Switch(this);
        pairingSwitch.setText("允许新设备配对（1 分钟）");
        pairingSwitch.setEnabled(false);
        content.addView(pairingSwitch);
        pairingSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!syncingPairing && controller != null) controller.setPairingEnabled(checked);
        });
        subscriberControls = new LinearLayout(this);
        subscriberControls.setOrientation(LinearLayout.VERTICAL);
        peerPicker = new Spinner(this);
        subscriberControls.addView(peerPicker);
        pairButton = new Button(this);
        pairButton.setText("配对所选 iOS 设备");
        subscriberControls.addView(pairButton);
        connectButton = new Button(this);
        connectButton.setText("连接所选 iOS 设备");
        subscriberControls.addView(connectButton);
        content.addView(subscriberControls);
        pairButton.setOnClickListener(v -> {
            SubscriberController.Peer selected = selectedPeer();
            if (controller instanceof SubscriberController && selected != null) {
                ((SubscriberController) controller).pair(selected);
            }
        });
        connectButton.setOnClickListener(v -> {
            SubscriberController.Peer selected = selectedPeer();
            if (controller instanceof SubscriberController && selected != null) {
                ((SubscriberController) controller).connect(selected);
            }
        });
        peer = label(content, "对端：—", 14);
        stats = label(content, "发送 0  ·  接收 0", 20);
        lastMessage = label(content, "每 5 秒双向心跳，收到后回复确认", 16);
        benchmarkStats = label(content, "UDP 吞吐：10 秒 / 目标 50 Mbps", 16);
        Button benchmark = new Button(this);
        benchmark.setText("测试本端 → 对端");
        content.addView(benchmark);
        benchmark.setOnClickListener(v -> {
            if (controller != null) controller.benchmark();
            else benchmarkStats.setText("请先建立数据链路");
        });
        instructions = label(content, "", 15);
        cacheButton = new Button(this);
        cacheButton.setText("管理配对缓存");
        content.addView(cacheButton);
        cacheButton.setOnClickListener(v -> {
            if (controller != null) return;
            if (cacheDialog != null) cacheDialog.dismiss();
            cacheDialog = new PairingCacheDialog(this);
            cacheDialog.show();
        });
        roles.setOnCheckedChangeListener((group, id) -> updateRole());
        updateRole();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        toggle.setOnClickListener(v -> {
            if (controller != null) { stop("已停止"); return; }
            for (String permission : PERMISSIONS) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(PERMISSIONS, 1);
                    return;
                }
            }
            start();
        });
    }

    private boolean subscribing() { return roles.getCheckedRadioButtonId() == SUBSCRIBER; }

    private void updateRole() {
        toggle.setText(controller != null ? "停止" : (subscribing() ? "开始订阅" : "开始发布"));
        pairingSwitch.setVisibility(subscribing() ? View.GONE : View.VISIBLE);
        subscriberControls.setVisibility(subscribing() ? View.VISIBLE : View.GONE);
        pairButton.setEnabled(controller != null && !discovered.isEmpty());
        connectButton.setEnabled(controller != null && !discovered.isEmpty());
        instructions.setText(subscribing()
                ? "1. iOS 选择发布角色，打开允许配对\n2. Android 开始订阅，选择设备并配对\n3. 输入 iOS 显示的代码，iOS 开始发布后点击连接\n\n请保持两端 App 在前台。"
                : "1. 点击开始发布\n2. 打开允许配对，iOS 添加设备并输入代码\n3. iOS 点击连接\n\n请保持两端 App 在前台。");
    }

    private TextView label(LinearLayout parent, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(0xff202c40);
        text.setPadding(0, 12, 0, 12);
        parent.addView(text);
        return text;
    }

    private void start() {
        if (controller != null) return;
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            state.setText("设备不支持 Wi-Fi Aware");
            return;
        }
        if (!getSystemService(LocationManager.class).isLocationEnabled()) {
            state.setText("无法启动：系统定位服务未开启");
            new AlertDialog.Builder(this)
                    .setTitle("无法启动 Wi-Fi Aware")
                    .setMessage("系统定位服务未开启。请打开定位服务，返回应用后重新开始。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("打开定位设置", (dialog, which) -> {
                        try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                        catch (ActivityNotFoundException e) {
                            state.setText("请手动前往系统设置开启定位服务");
                            Toast.makeText(this, "请手动前往系统设置开启定位服务", Toast.LENGTH_LONG).show();
                        }
                    }).show();
            return;
        }
        peer.setText("对端：—");
        stats.setText("发送 0  ·  接收 0");
        lastMessage.setText("等待心跳…");
        SubscriberController.Observer observer = new SubscriberController.Observer() {
            @Override public void benchmark(String text) { benchmarkStats.setText(text); }
            @Override public void status(String value) { state.setText(value); }
            @Override public void pin(String value) {
                pin.setText(value.isEmpty() ? "" : "配对代码\n" + value);
                if (value.isEmpty() && pinDialog != null) {
                    pinDialog.dismiss();
                    pinDialog = null;
                }
            }
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
                controller = null;
                sessionControls(false);
            }
            @Override public void peers(List<SubscriberController.Peer> peers) { updatePeers(peers); }
            @Override public void pinRequested(SubscriberController.Peer peer) { promptPin(peer); }
        };
        controller = subscribing() ? new SubscriberController(this, observer) : new AwareController(this, observer);
        sessionControls(true);
        controller.start();
    }

    private void sessionControls(boolean running) {
        if (!running) benchmarkStats.setText("UDP 吞吐：10 秒 / 目标 50 Mbps");
        cacheButton.setEnabled(!running);
        cacheButton.setText(running ? "停止后可管理配对缓存" : "管理配对缓存");
        for (int i = 0; i < roles.getChildCount(); i++) roles.getChildAt(i).setEnabled(!running);
        if (running) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateRole();
    }

    private SubscriberController.Peer selectedPeer() {
        int index = peerPicker.getSelectedItemPosition();
        return index >= 0 && index < discovered.size() ? discovered.get(index) : null;
    }

    private void updatePeers(List<SubscriberController.Peer> peers) {
        SubscriberController.Peer selected = selectedPeer();
        discovered.clear();
        discovered.addAll(peers);
        List<String> names = new ArrayList<>();
        for (SubscriberController.Peer peer : discovered) names.add(peer.toString());
        if (names.isEmpty()) names.add("尚未发现 iOS 服务");
        peerPicker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                names));
        if (selected != null && discovered.contains(selected)) peerPicker.setSelection(discovered.indexOf(selected));
        updateRole();
    }

    private void promptPin(SubscriberController.Peer peer) {
        if (!(controller instanceof SubscriberController) || pinDialog != null) return;
        SubscriberController target = (SubscriberController) controller;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("iOS 显示的配对代码");
        pinDialog = new AlertDialog.Builder(this)
                .setTitle("与 " + peer.name + " 配对")
                .setView(input)
                .setNegativeButton("取消", (dialog, which) -> target.cancelPairing())
                .setPositiveButton("配对", null)
                .setOnCancelListener(dialog -> target.cancelPairing())
                .create();
        pinDialog.setOnDismissListener(dialog -> pinDialog = null);
        pinDialog.setOnShowListener(dialog -> pinDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = input.getText().toString().trim();
            if (!code.matches("[0-9]{4,12}")) { input.setError("请输入 iOS 显示的完整数字代码"); return; }
            pinDialog.dismiss();
            target.submitPin(peer, code);
        }));
        pinDialog.show();
    }

    private void stop(String status) {
        if (controller != null) { controller.close(); controller = null; }
        sessionControls(false);
        state.setText(status);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != 1) return;
        for (String permission : PERMISSIONS) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                state.setText("需要附近设备和定位权限才能发现并连接");
                return;
            }
        }
        start();
    }
    @Override protected void onStop() {
        super.onStop();
        if (cacheDialog != null) { cacheDialog.dismiss(); cacheDialog = null; }
        stop("已暂停；回到前台后请重新开始");
    }
    @Override protected void onDestroy() { stop("已停止"); super.onDestroy(); }
}
