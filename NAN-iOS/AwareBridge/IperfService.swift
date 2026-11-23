import Foundation
import Network
import WiFiAware

/// Publisher-side libiperf uses loopback only; Network framework accepts radio traffic.
@MainActor final class IperfService {
    private var task: Task<Void, Never>?
    private var relay: IperfRelay?
    private var closed = false
    private(set) var ready = false

    func start(from connection: NetworkConnection<UDP>) async throws {
        guard task == nil, !closed else { return }
        relay = try await IperfRelay.accept(from: connection)
        task = Task {
            while !Task.isCancelled && !closed {
                let result = await IperfClient.runNative(server: true, address: "::1", port: 5202,
                    udp: false, reverse: false, streams: 4, duration: 10, rate: 0)
                if !closed {
                    print("libiperf publisher cycle: \(result)")
                    try? await Task.sleep(for: .milliseconds(150))
                }
            }
        }
        for _ in 0..<100 {
            try Task.checkCancellation()
            if closed { throw CancellationError() }
            if ab_ready() != 0 { ready = true; return }
            try await Task.sleep(for: .milliseconds(50))
        }
        close()
        throw NSError(domain: "IperfService", code: 1,
                      userInfo: [NSLocalizedDescriptionKey: "发布端测速服务启动超时"])
    }
    var available: Bool { ready && !closed && ab_ready() != 0 }
    func close() {
        if closed { return }
        closed = true; ready = false
        task?.cancel(); task = nil
        relay?.close(); relay = nil
    }
}
