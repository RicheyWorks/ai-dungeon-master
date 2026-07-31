package com.xai.dungeonmaster.android

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal STOMP 1.2 client over a native WebSocket (Spring's `/ws-stomp`).
 *
 * Enough for the Android shell: CONNECT (optional Bearer), SUBSCRIBE, SEND,
 * and MESSAGE delivery. Heartbeats are disabled (`heart-beat:0,0`).
 */
class StompClient(
    private val wsUrl: String,
    private val token: String? = null,
    private val listener: Listener,
    private val client: OkHttpClient = defaultClient(),
) {
    interface Listener {
        fun onConnected()
        fun onMessage(destination: String, body: String)
        fun onError(message: String)
        fun onClosed()
    }

    private var socket: WebSocket? = null
    private val connected = AtomicBoolean(false)
    private val subSeq = AtomicInteger(0)
    private val buffer = StringBuilder()

    fun isConnected(): Boolean = connected.get()

    fun connect() {
        if (socket != null) return
        val request = Request.Builder().url(wsUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(buildConnectFrame())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncoming(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleIncoming(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected.set(false)
                listener.onError(t.message ?: t.javaClass.simpleName)
                listener.onClosed()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected.set(false)
                listener.onClosed()
            }
        })
    }

    fun subscribe(destination: String): String {
        val id = "sub-${subSeq.getAndIncrement()}"
        sendRaw(buildFrame("SUBSCRIBE", mapOf(
            "id" to id,
            "destination" to destination,
            "ack" to "auto",
        ), null))
        return id
    }

    fun send(destination: String, body: String, contentType: String = "application/json") {
        sendRaw(buildFrame("SEND", mapOf(
            "destination" to destination,
            "content-type" to contentType,
            "content-length" to body.toByteArray(Charsets.UTF_8).size.toString(),
        ), body))
    }

    fun disconnect() {
        try {
            if (connected.get()) {
                sendRaw(buildFrame("DISCONNECT", mapOf("receipt" to "bye"), null))
            }
        } catch (_: Exception) {
            // ignore
        }
        socket?.close(1000, "bye")
        socket = null
        connected.set(false)
    }

    private fun sendRaw(frame: String) {
        val ws = socket ?: return
        ws.send(frame)
    }

    private fun handleIncoming(chunk: String) {
        buffer.append(chunk)
        while (true) {
            val nullIdx = buffer.indexOf("\u0000")
            if (nullIdx < 0) break
            val raw = buffer.substring(0, nullIdx)
            buffer.delete(0, nullIdx + 1)
            if (raw.isBlank()) continue
            dispatchFrame(raw)
        }
    }

    private fun dispatchFrame(raw: String) {
        val normalized = raw.trimStart('\n', '\r')
        if (normalized.isEmpty()) return
        val split = normalized.split("\n\n", limit = 2)
        val headerBlock = split[0]
        val body = if (split.size > 1) split[1] else ""
        val lines = headerBlock.split("\n")
        if (lines.isEmpty()) return
        val command = lines[0].trim()
        val headers = linkedMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.substring(0, colon)] = line.substring(colon + 1)
            }
        }
        when (command) {
            "CONNECTED" -> {
                connected.set(true)
                listener.onConnected()
            }
            "MESSAGE" -> {
                val dest = headers["destination"] ?: ""
                listener.onMessage(dest, body)
            }
            "ERROR" -> {
                listener.onError(body.ifBlank { headers["message"] ?: "STOMP error" })
            }
            "RECEIPT" -> { /* ignore */ }
            else -> { /* ignore unknown */ }
        }
    }

    private fun buildConnectFrame(): String {
        val headers = linkedMapOf(
            "accept-version" to "1.2,1.1,1.0",
            "host" to hostOf(wsUrl),
            "heart-beat" to "0,0",
        )
        if (!token.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $token"
            headers["X-Auth-Token"] = token
        }
        return buildFrame("CONNECT", headers, null)
    }

    private fun buildFrame(command: String, headers: Map<String, String>, body: String?): String {
        val sb = StringBuilder()
        sb.append(command).append('\n')
        headers.forEach { (k, v) -> sb.append(k).append(':').append(v).append('\n') }
        sb.append('\n')
        if (body != null) sb.append(body)
        sb.append('\u0000')
        return sb.toString()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket
            .build()

        fun hostOf(wsUrl: String): String {
            return try {
                val without = wsUrl.removePrefix("ws://").removePrefix("wss://")
                without.substringBefore('/').substringBefore(':').ifBlank { "localhost" }
            } catch (_: Exception) {
                "localhost"
            }
        }

        /** Convert http(s) base URL to native STOMP endpoint. */
        fun stompUrl(httpBase: String): String {
            val base = httpBase.trimEnd('/')
            val ws = when {
                base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
                base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
                base.startsWith("ws://") || base.startsWith("wss://") -> base
                else -> "ws://$base"
            }
            return "$ws/ws-stomp"
        }
    }
}
