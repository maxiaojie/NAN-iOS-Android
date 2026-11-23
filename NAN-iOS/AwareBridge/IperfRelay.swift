import Foundation
import Network
import WiFiAware
import OSLog

/// Keeps libiperf's local sockets on loopback; all radio traffic uses public Wi-Fi Aware APIs.
@MainActor final class IperfRelay {
    private var tasks: [Task<Void, Never>] = []
    private var readyCount = 0
    private var failure: String?
    private var browser: NWBrowser?
    private var discoveredEndpoint: WAEndpoint?
    private var connectedEndpoint: NWEndpoint?
    private var closed = false
    private static let logger = Logger(subsystem: "io.awarebridge.ios", category: "iperf")
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
            if path.endpoint.publishedService != nil,
               let remote = connection.remoteEndpoint ?? connection.currentPath?.remoteEndpoint {
                // Retain the accepted connection's scoped Aware address. The peer
                // is a subscriber and has no published service for a new browser.
                relay.connectedEndpoint = NWEndpoint(copying: remote, newPort: 5201)
                Self.logger.notice("Copied incoming Aware endpoint for iperf: \(relay.connectedEndpoint != nil)")
            }
            if relay.connectedEndpoint == nil {
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
        }
        guard relay.discoveredEndpoint != nil || relay.connectedEndpoint != nil else {
            relay.close()
            throw NSError(domain: "IperfRelay", code: 5, userInfo: [NSLocalizedDescriptionKey: "No additional Aware endpoint discovered"])
        }
        Self.logger.notice("Starting iperf relay to Aware endpoint")
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
                            let remote: NetworkConnection<TCP>
                            if let endpoint = relay.discoveredEndpoint {
                                remote = NetworkConnection(to: endpoint, using: .parameters { TCP() }
                                    .wifiAware { $0.performanceMode = .bulk })
                            } else {
                                remote = NetworkConnection(to: relay.connectedEndpoint!, using: .parameters { TCP() }
                                    .wifiAware { $0.performanceMode = .bulk })
                            }
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
                            let remote: NetworkConnection<UDP>
                            if let endpoint = relay.discoveredEndpoint {
                                remote = NetworkConnection(to: endpoint, using: .parameters { UDP() }
                                    .wifiAware { $0.performanceMode = .bulk })
                            } else {
                                remote = NetworkConnection(to: relay.connectedEndpoint!, using: .parameters { UDP() }
                                    .wifiAware { $0.performanceMode = .bulk })
                            }
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
    /// Additional listeners reuse the existing NDP without advertising another service.
    static func accept(from connection: NetworkConnection<UDP>) async throws -> IperfRelay {
        guard #available(iOS 26.4, *), let path = try await connection.currentPath?.wifiAware else {
            throw NSError(domain: "IperfRelay", code: 6,
                userInfo: [NSLocalizedDescriptionKey: "测速服务需要 iOS 26.4+ 和已建立的 Aware 链路"])
        }
        let relay = IperfRelay()
        let tcp = try NetworkListener(for: .wifiAware(.addingConnections(from: .selected([path.endpoint.device]))),
            using: .parameters { TCP() }.localPort(5201).wifiAware { $0.performanceMode = .bulk })
        let udp = try NetworkListener(for: .wifiAware(.addingConnections(from: .selected([path.endpoint.device]))),
            using: .parameters { UDP() }.localPort(5201).wifiAware { $0.performanceMode = .bulk })
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
                try await tcp.run { remote in
                    guard !relay.closed else { return }
                    relay.tasks.append(Task {
                        do {
                            let local = NetworkConnection(to: .hostPort(host: "::1", port: 5202), using: .parameters { TCP() })
                            try await Self.pipeTCP(local, remote)
                        } catch { relay.failure = String(describing: error) }
                    })
                }
            } catch { relay.failure = String(describing: error) }
        })
        relay.tasks.append(Task {
            do {
                try await udp.run { remote in
                    guard !relay.closed else { return }
                    relay.tasks.append(Task {
                        do {
                            let local = NetworkConnection(to: .hostPort(host: "::1", port: 5202), using: .parameters { UDP() })
                            try await Self.pipeUDP(local, remote)
                        } catch { relay.failure = String(describing: error) }
                    })
                }
            } catch { relay.failure = String(describing: error) }
        })
        do {
            for _ in 0..<100 {
                try Task.checkCancellation()
                if let failure = relay.failure { throw NSError(domain: "IperfRelay", code: 7,
                    userInfo: [NSLocalizedDescriptionKey: failure]) }
                if relay.readyCount == 2 { return relay }
                try await Task.sleep(for: .milliseconds(50))
            }
            throw NSError(domain: "IperfRelay", code: 8,
                userInfo: [NSLocalizedDescriptionKey: "Aware 测速监听启动超时"])
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
