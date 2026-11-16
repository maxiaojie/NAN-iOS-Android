import SwiftUI
import DeviceDiscoveryUI
import WiFiAware

@main
struct AwareBridgeApp: App {
    var body: some Scene { WindowGroup { BridgeView() } }
}

struct BridgeView: View {
    @State private var model = BridgeModel()
    @Environment(\.scenePhase) private var scenePhase
    private let clock = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label("Wi-Fi Aware · iOS ↔ Android", systemImage: "wifi")
                    Text(model.status).font(.headline)
                }
                Section("配对与连接") {
                    Picker("角色", selection: $model.role) {
                        ForEach(BridgeRole.allCases) { role in
                            Text(role.rawValue).tag(role)
                        }
                    }.disabled(model.active)
                    if model.role == .publisher {
                        Button(model.active ? "停止发布" : "开始发布") {
                            if model.active { model.stop() } else { model.publish() }
                        }
                        DevicePairingView(.wifiAware(.connecting(to: .bridge, from: .userSpecifiedDevices))) {
                            Label("允许 Android 配对", systemImage: "plus.circle")
                        } fallback: {
                            Text("当前设备无法使用 Wi-Fi Aware 配对")
                        }
                        .disabled(model.active)
                        Text("先打开允许配对；Android 开始订阅并选择本机，在 Android 输入 iOS 显示的代码。配对后关闭系统页面，再开始发布，Android 点击连接。")
                            .foregroundStyle(.secondary)
                    } else {
                        DevicePicker(.wifiAware(.connecting(to: .userSpecifiedDevices, from: .bridge))) { endpoint in
                            model.didPair(endpoint)
                        } label: {
                            Label("添加 Android 设备", systemImage: "plus.circle")
                        } fallback: {
                            Text("当前设备无法使用 Wi-Fi Aware 配对")
                        }
                        .disabled(model.active)
                        if model.devices.isEmpty {
                            Text("先在 Android 开始发布，再添加设备并输入 Android 显示的配对代码。")
                                .foregroundStyle(.secondary)
                        } else {
                            Picker("设备", selection: $model.selectedDeviceID) {
                                ForEach(model.devices) { device in
                                    Text(model.displayName(device)).tag(Optional(device.id))
                                }
                            }.disabled(model.active)
                        }
                        Button(model.active ? "断开" : "连接") {
                            if model.active { model.stop() } else { model.connect() }
                        }.disabled(!model.active && model.selectedDeviceID == nil)
                    }
                }
                Section("数据链路") {
                    LabeledContent("对端", value: model.peer)
                    LabeledContent("发送 / 接收", value: "\(model.tx) / \(model.rx)")
                    if let date = model.lastReceived {
                        LabeledContent("最近接收", value: "\(max(0, Int(model.now.timeIntervalSince(date)))) 秒前")
                    }
                    Text(model.lastMessage).font(.footnote).foregroundStyle(.secondary)
                }
                Section {
                    Text("两端每 5 秒发送心跳并回复确认。请保持两端 App 在前台；切到后台会主动断开。")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("AwareBridge")
        }
        .task { await model.watchDevices() }
        .onReceive(clock) { _ in model.tick() }
        .onChange(of: model.active) { _, active in UIApplication.shared.isIdleTimerDisabled = active }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { model.stop(); UIApplication.shared.isIdleTimerDisabled = false }
        }
    }
}
