import Foundation

@MainActor
public protocol StompClientListener: AnyObject {
    func stompDidConnect(_ client: StompClient)
    func stomp(_ client: StompClient, didReceive destination: String, body: String)
    func stomp(_ client: StompClient, didFail message: String)
    func stompDidClose(_ client: StompClient)
}

/// Minimal STOMP 1.2 client over a native WebSocket (`/ws-stomp`).
@MainActor
public final class StompClient: NSObject {
    public private(set) var isConnected = false

    private let wsURL: URL
    private let token: String?
    private weak var listener: StompClientListener?
    private var task: URLSessionWebSocketTask?
    private var session: URLSession?
    private var buffer = ""
    private var subSeq = 0

    public init(wsURL: URL, token: String? = nil, listener: StompClientListener?) {
        self.wsURL = wsURL
        self.token = token
        self.listener = listener
        super.init()
    }

    public static func stompURL(httpBase: String) -> URL? {
        var base = httpBase.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if base.hasPrefix("https://") {
            base = "wss://" + base.dropFirst("https://".count)
        } else if base.hasPrefix("http://") {
            base = "ws://" + base.dropFirst("http://".count)
        } else if !(base.hasPrefix("ws://") || base.hasPrefix("wss://")) {
            base = "ws://" + base
        }
        return URL(string: base + "/ws-stomp")
    }

    public func connect() {
        guard task == nil else { return }
        let config = URLSessionConfiguration.default
        let session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
        self.session = session
        let task = session.webSocketTask(with: wsURL)
        self.task = task
        task.resume()
        sendRaw(buildConnectFrame())
        receiveLoop()
    }

    public func subscribe(destination: String) -> String {
        let id = "sub-\(subSeq)"
        subSeq += 1
        sendRaw(buildFrame(
            command: "SUBSCRIBE",
            headers: ["id": id, "destination": destination, "ack": "auto"],
            body: nil
        ))
        return id
    }

    public func send(destination: String, body: String, contentType: String = "application/json") {
        let bytes = Array(body.utf8)
        sendRaw(buildFrame(
            command: "SEND",
            headers: [
                "destination": destination,
                "content-type": contentType,
                "content-length": "\(bytes.count)",
            ],
            body: body
        ))
    }

    public func disconnect() {
        if isConnected {
            sendRaw(buildFrame(command: "DISCONNECT", headers: ["receipt": "bye"], body: nil))
        }
        task?.cancel(with: .goingAway, reason: nil)
        task = nil
        session?.invalidateAndCancel()
        session = nil
        isConnected = false
    }

    private func receiveLoop() {
        task?.receive { [weak self] result in
            guard let self else { return }
            Task { @MainActor in
                switch result {
                case .failure(let error):
                    self.isConnected = false
                    self.listener?.stomp(self, didFail: error.localizedDescription)
                    self.listener?.stompDidClose(self)
                case .success(let message):
                    switch message {
                    case .string(let text):
                        self.handleIncoming(text)
                    case .data(let data):
                        if let text = String(data: data, encoding: .utf8) {
                            self.handleIncoming(text)
                        }
                    @unknown default:
                        break
                    }
                    self.receiveLoop()
                }
            }
        }
    }

    private func handleIncoming(_ chunk: String) {
        buffer += chunk
        while let nullIdx = buffer.firstIndex(of: "\0") {
            let raw = String(buffer[..<nullIdx])
            buffer = String(buffer[buffer.index(after: nullIdx)...])
            if !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                dispatchFrame(raw)
            }
        }
    }

    private func dispatchFrame(_ raw: String) {
        let normalized = raw.trimmingCharacters(in: CharacterSet(charactersIn: "\n\r"))
        guard !normalized.isEmpty else { return }
        let parts = normalized.split(separator: "\n\n", maxSplits: 1, omittingEmptySubsequences: false)
        let headerBlock = String(parts[0])
        let body = parts.count > 1 ? String(parts[1]) : ""
        let lines = headerBlock.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        guard let command = lines.first?.trimmingCharacters(in: .whitespaces) else { return }
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            if let colon = line.firstIndex(of: ":") {
                let key = String(line[..<colon])
                let value = String(line[line.index(after: colon)...])
                headers[key] = value
            }
        }
        switch command {
        case "CONNECTED":
            isConnected = true
            listener?.stompDidConnect(self)
        case "MESSAGE":
            listener?.stomp(self, didReceive: headers["destination"] ?? "", body: body)
        case "ERROR":
            listener?.stomp(self, didFail: body.isEmpty ? (headers["message"] ?? "STOMP error") : body)
        default:
            break
        }
    }

    private func sendRaw(_ frame: String) {
        task?.send(.string(frame)) { [weak self] error in
            if let error, let self {
                Task { @MainActor in
                    self.listener?.stomp(self, didFail: error.localizedDescription)
                }
            }
        }
    }

    private func buildConnectFrame() -> String {
        var headers: [String: String] = [
            "accept-version": "1.2,1.1,1.0",
            "host": wsURL.host ?? "localhost",
            "heart-beat": "0,0",
        ]
        if let token, !token.isEmpty {
            headers["Authorization"] = "Bearer \(token)"
            headers["X-Auth-Token"] = token
        }
        return buildFrame(command: "CONNECT", headers: headers, body: nil)
    }

    private func buildFrame(command: String, headers: [String: String], body: String?) -> String {
        var sb = command + "\n"
        for (k, v) in headers {
            sb += "\(k):\(v)\n"
        }
        sb += "\n"
        if let body { sb += body }
        sb += "\0"
        return sb
    }
}

extension StompClient: URLSessionWebSocketDelegate {
    nonisolated public func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didOpenWithProtocol protocol: String?
    ) {}

    nonisolated public func urlSession(
        _ session: URLSession,
        webSocketTask: URLSessionWebSocketTask,
        didCloseWith closeCode: URLSessionWebSocketTask.CloseCode,
        reason: Data?
    ) {
        Task { @MainActor in
            self.isConnected = false
            self.listener?.stompDidClose(self)
        }
    }
}
