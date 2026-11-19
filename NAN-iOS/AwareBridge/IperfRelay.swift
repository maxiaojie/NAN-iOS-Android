import Foundation
import Network
import WiFiAware

/// Keeps libiperf's local sockets on loopback; all radio traffic uses public Wi-Fi Aware APIs.
@MainActor final class IperfRelay {
    private var tasks: [Task<Void, Never>] = []
    private var readyCount = 0
    private var failure: String?
    private var browser: NWBrowser?
    private var discoveredEndpoint: WAEndpoint?
    private var closed = false
    static func open(from connection: NetworkConnection<UDP>) async throws -> IperfRelay {
        guard #available(iOS 26.4, *) else {
            throw NSError(domain: "IperfRelay", code: 1, userInfo: [NSLocalizedDescriptionKey: "性能测试需要 iOS 26.4+"])
        }
        let relay = IperfRelay()
        if let direct = connection.remoteEndpoint?.wifiAware(port: 5201)
            ?? connection.currentPath?.remoteEndpoint?.wifiAware(port: 5201) {
            relay.discoveredEndpoint = direct
        } else {
            guard let path = try await connection.currentPath?.wifiAware else {
                throw NSError(domain: "IperfRelay", code: 4, userInfo: [NSLocalizedDescriptionKey: "No Aware path"])
            }
            let provider: WASubscriberBrowser = .wifiAware(.connecting(to: .selected([path.endpoint.device]), from: .bridge))
            let browser = NWBrowser(for: provider.makeDescriptor(), using: provider.configureParameters(.udp))
            relay.browser = browser
            browser.browseResultsChangedHandler = { results, _ in
                Task { @MainActor in
                    guard !relay.closed else { return }
                    relay.discoveredEndpoint = results.compactMap { $0.endpoint.wifiAware(port: 5201) }.first
                }
            }
            browser.start(queue: .global(qos: .userInitiated))
            do {
                for _ in 0..<200 {
                    if relay.discoveredEndpoint != nil { break }
                    try await Task.sleep(for: .milliseconds(50))
                }
            } catch { relay.close(); throw error }
        }
        guard let endpoint = relay.discoveredEndpoint else {
            relay.close()
            throw NSError(domain: "IperfRelay", code: 5, userInfo: [NSLocalizedDescriptionKey: "No additional Aware endpoint discovered"])
        }
        let tcp = try NetworkListener(using: .parameters { TCP() }
            .localEndpoint(.hostPort(host: "::1", port: 5201)))
        let udp = try NetworkListener(using: .parameters { UDP() }
            .localEndpoint(.hostPort(host: "::1", port: 5201)))
        tcp.onStateUpdate { _, state in
            if case .ready = state { relay.readyCount += 1 }
            if case .failed(let error) = state { relay.failure = String(describing: error) }
        }
        udp.onStateUpdate { _, state in
            if case .ready = state { relay.readyCount += 1 }
            if case .failed(let error) = state { relay.failure = String(describing: error) }
        }
        relay.tasks.append(Task {
            do {
                try await tcp.run { local in
                    guard !relay.closed else { return }
                    relay.tasks.append(Task {
                        do {
                            let remote = NetworkConnection(to: endpoint, using: .parameters { TCP() }
                                .wifiAware { $0.performanceMode = .bulk })
                            try await Self.pipeTCP(local, remote)
                        } catch { relay.failure = String(describing: error) }
                    })
                }
            } catch { relay.failure = String(describing: error) }
        })
        relay.tasks.append(Task {
            do {
                try await udp.run { local in
                    guard !relay.closed else { return }
                    relay.tasks.append(Task {
                        do {
                            let remote = NetworkConnection(to: endpoint, using: .parameters { UDP() }
                                .wifiAware { $0.performanceMode = .bulk })
                            try await Self.pipeUDP(local, remote)
                        } catch { relay.failure = String(describing: error) }
                    })
                }
            } catch { relay.failure = String(describing: error) }
        })
        do {
            for _ in 0..<60 {
                if let failure = relay.failure { throw NSError(domain: "IperfRelay", code: 2, userInfo: [NSLocalizedDescriptionKey: failure]) }
                if relay.readyCount == 2 { return relay }
                try await Task.sleep(for: .milliseconds(50))
            }
            throw NSError(domain: "IperfRelay", code: 3, userInfo: [NSLocalizedDescriptionKey: "本地性能适配层启动超时"])
        } catch { relay.close(); throw error }
    }
    private static func pipeTCP(_ a: NetworkConnection<TCP>, _ b: NetworkConnection<TCP>) async throws {
        try await withThrowingTaskGroup(of: Void.self) { group in
            for (source, destination) in [(a,b),(b,a)] {
                group.addTask {
                    while !Task.isCancelled {
                        let (data, _) = try await source.receive(atMost: 131072)
                        if data.isEmpty { try await destination.send(Data(), endOfStream: true); return }
                        try await destination.send(data)
                    }
                }
            }
            defer { group.cancelAll() }
            try await group.next()
        }
    }
    private static func pipeUDP(_ a: NetworkConnection<UDP>, _ b: NetworkConnection<UDP>) async throws {
        try await withThrowingTaskGroup(of: Void.self) { group in
            for (source, destination) in [(a,b),(b,a)] {
                group.addTask {
                    while !Task.isCancelled {
                        let (data, _) = try await source.receive()
                        try await destination.send(data)
                    }
                }
            }
            defer { group.cancelAll() }
            try await group.next()
        }
    }
    func close() { closed = true; browser?.cancel(); browser = nil; tasks.forEach { $0.cancel() }; tasks.removeAll() }
}
