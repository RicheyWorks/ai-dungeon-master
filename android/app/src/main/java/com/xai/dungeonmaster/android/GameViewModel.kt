package com.xai.dungeonmaster.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xai.dungeonmaster.client.apis.V2Api
import com.xai.dungeonmaster.client.models.ActionRequest
import com.xai.dungeonmaster.client.models.CatalogPayload
import com.xai.dungeonmaster.client.models.EntitlementPayload
import com.xai.dungeonmaster.client.models.GameStatusV2
import com.xai.dungeonmaster.client.models.NarrateRequest
import com.xai.dungeonmaster.client.models.SessionRequest
import com.xai.dungeonmaster.client.models.VerifyReceiptRequest
import com.xai.dungeonmaster.client.infrastructure.ClientException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Bridges the synchronous generated SDK (jvm-okhttp4) to Compose.
 *
 * On first contact the ViewModel mints a guest session via the generated
 * `createSessionV2` call and attaches the JWT to every subsequent request
 * through [HttpClients]. After a session is ready it also opens a native
 * STOMP socket (`/ws-stomp`) for live narration.
 */
class GameViewModel : ViewModel() {

    /** 10.0.2.2 is the emulator's alias for the host machine's localhost. */
    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }

    data class UiState(
        val baseUrl: String = DEFAULT_BASE_URL,
        val session: SessionInfo? = null,
        val status: GameStatusV2? = null,
        val narration: String? = null,
        /** Live stream buffer while narrative_chunk frames arrive over STOMP. */
        val streamBuffer: String = "",
        val stompConnected: Boolean = false,
        val catalog: CatalogPayload? = null,
        val entitlements: EntitlementPayload? = null,
        val lastSavePath: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val stompRef = AtomicReference<StompClient?>(null)
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val envelopeAdapter = moshi.adapter(WsEnvelope::class.java)

    private fun base(): String = _state.value.baseUrl.trimEnd('/')

    private fun api(): V2Api = V2Api(basePath = base(), client = HttpClients.client())

    /** Save/load/reset until those ops land in the generated SDK. */
    private fun gameExtras(): SessionClient = SessionClient(base(), HttpClients.client())

    fun setBaseUrl(url: String) {
        if (url.trimEnd('/') != _state.value.baseUrl.trimEnd('/')) {
            disconnectStomp()
            HttpClients.clearToken()
            _state.value = _state.value.copy(
                baseUrl = url,
                session = null,
                status = null,
                stompConnected = false,
            )
        } else {
            _state.value = _state.value.copy(baseUrl = url)
        }
    }

    /** Mint a guest session (or re-mint) via generated SDK and open STOMP. */
    fun startSession(displayName: String? = null) = launchCall { current ->
        disconnectStomp()
        val info = mintSession(displayName)
        HttpClients.setToken(info.token)
        connectStomp(info)
        current.copy(
            session = info,
            error = null,
            info = "Session ${info.shortId()} · ${info.displayName}",
        )
    }

    /** Ensure a session exists, then fetch game status. */
    fun refresh() = launchCall { current ->
        val withSession = ensureSession(current)
        connectStomp(withSession.session!!)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, error = null, info = null)
    }

    fun act(choiceLabel: String) = launchCall { current ->
        val withSession = ensureSession(current)
        connectStomp(withSession.session!!)
        val stomp = stompRef.get()
        if (stomp != null && stomp.isConnected()) {
            val body = """{"choiceLabel":${jsonString(choiceLabel)}}"""
            stomp.send("/app/action", body)
            val envelope = api().getStatusV2()
            withSession.copy(status = envelope.payload, error = null, info = "Action sent via WS")
        } else {
            val envelope = api().submitActionV2(ActionRequest(choiceLabel))
            withSession.copy(status = envelope.payload, error = null, info = null)
        }
    }

    /**
     * Stream narration over STOMP when connected; otherwise REST round-trip.
     */
    fun narrate(prompt: String) = launchCall { current ->
        val withSession = ensureSession(current)
        connectStomp(withSession.session!!)
        val stomp = stompRef.get()
        if (stomp != null && stomp.isConnected()) {
            _state.value = _state.value.copy(streamBuffer = "", narration = null)
            val body = """{"prompt":${jsonString(prompt)}}"""
            stomp.send("/app/narrate", body)
            withSession.copy(
                streamBuffer = "",
                info = "Streaming narration…",
                error = null,
            )
        } else {
            val envelope = api().narrateV2(narrateRequest = NarrateRequest(prompt))
            withSession.copy(narration = envelope.payload.text, error = null, info = "REST narrate")
        }
    }

    fun loadCatalog() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().getCatalogV2()
        withSession.copy(catalog = envelope.payload, error = null, info = null)
    }

    fun togglePack(id: String, enable: Boolean) = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = if (enable) api().enablePackV2(id) else api().disablePackV2(id)
        withSession.copy(catalog = envelope.payload, error = null, info = null)
    }

    fun loadEntitlements() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().listEntitlementsV2()
        withSession.copy(entitlements = envelope.payload, error = null, info = null)
    }

    /**
     * Verify a store receipt for the current session. 402 (payment required /
     * rejected receipt) is surfaced as an error with the server reason when
     * available.
     */
    fun verifyReceipt(productId: String, receipt: String, storefront: String) = launchCall { current ->
        val withSession = ensureSession(current)
        try {
            val envelope = api().verifyReceiptV2(
                VerifyReceiptRequest(
                    productId = productId,
                    receipt = receipt,
                    storefront = storefront.ifBlank { DevReceipts.STOREFRONT_ID },
                ),
            )
            val p = envelope.payload
            withSession.copy(
                entitlements = p,
                info = if (p.granted == true) "Granted ${p.productId}" else "Not granted: ${p.reason}",
                error = null,
            )
        } catch (e: ClientException) {
            // 402 from a rejected receipt — refresh list and show reason.
            val listed = try {
                api().listEntitlementsV2().payload
            } catch (_: Exception) {
                withSession.entitlements
            }
            withSession.copy(
                entitlements = listed,
                error = "Receipt rejected (${e.statusCode}): ${e.message}",
                info = null,
            )
        }
    }

    /** Mint a DevStorefront-compatible receipt and verify it in one step. */
    fun devPurchase(productId: String) = launchCall { current ->
        val withSession = ensureSession(current)
        val receipt = DevReceipts.sign(productId)
        try {
            val envelope = api().verifyReceiptV2(
                VerifyReceiptRequest(
                    productId = productId,
                    receipt = receipt,
                    storefront = DevReceipts.STOREFRONT_ID,
                ),
            )
            val p = envelope.payload
            withSession.copy(
                entitlements = p,
                info = if (p.granted == true) {
                    "Dev purchase granted: ${p.productId}"
                } else {
                    "Dev purchase failed: ${p.reason}"
                },
                error = null,
            )
        } catch (e: ClientException) {
            withSession.copy(
                error = "Dev purchase failed (${e.statusCode}): ${e.message}",
                info = null,
            )
        }
    }

    fun saveGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        val result = gameExtras().save(token)
        withSession.copy(
            lastSavePath = result.path,
            info = if (result.saved) "Saved${if (result.sessionScoped) " (session)" else ""}" else "Save failed",
            error = null,
        )
    }

    fun loadGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        gameExtras().load(token)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, info = "Loaded save", error = null)
    }

    fun resetGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        gameExtras().reset(token)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, info = "New adventure started", error = null)
    }

    private fun mintSession(displayName: String?): SessionInfo {
        val req = displayName?.takeIf { it.isNotBlank() }?.let { SessionRequest(displayName = it) }
        // Unauthenticated mint: use a clean client so we don't send a stale Bearer.
        val bare = V2Api(basePath = base())
        val envelope = bare.createSessionV2(sessionRequest = req)
        val p = envelope.payload
        val token = p.token ?: throw IllegalStateException("Session token missing from createSessionV2")
        return SessionInfo(
            sessionId = p.sessionId,
            token = token,
            displayName = p.displayName,
            expiresAtEpochSeconds = p.expiresAtEpochSeconds ?: 0L,
            createdAtEpochSeconds = p.createdAtEpochSeconds ?: 0L,
        )
    }

    private fun ensureSession(current: UiState): UiState {
        val existing = current.session
        if (existing != null && HttpClients.token() != null) {
            return current
        }
        val info = mintSession(existing?.displayName)
        HttpClients.setToken(info.token)
        return current.copy(session = info)
    }

    private fun connectStomp(session: SessionInfo) {
        val existing = stompRef.get()
        if (existing != null && existing.isConnected()) return

        disconnectStomp()
        val url = StompClient.stompUrl(base())
        val client = StompClient(url, session.token, object : StompClient.Listener {
            override fun onConnected() {
                stompRef.get()?.subscribe("/topic/narrative")
                stompRef.get()?.subscribe("/topic/narrative/${session.sessionId}")
                publish { it.copy(stompConnected = true, info = "Live stream connected") }
            }

            override fun onMessage(destination: String, body: String) {
                handleStompBody(body)
            }

            override fun onError(message: String) {
                publish { it.copy(stompConnected = false, error = "WS: $message") }
            }

            override fun onClosed() {
                publish { it.copy(stompConnected = false) }
            }
        })
        stompRef.set(client)
        client.connect()
    }

    private fun handleStompBody(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("{")) {
            try {
                val env = envelopeAdapter.fromJson(trimmed)
                when (env?.type) {
                    "narrative_chunk" -> {
                        val chunk = env.payload?.chunk ?: env.payload?.text ?: return
                        publish {
                            it.copy(streamBuffer = it.streamBuffer + chunk, info = null)
                        }
                        return
                    }
                    "narrative_update" -> {
                        val text = env.payload?.text
                            ?: _state.value.streamBuffer.takeIf { it.isNotBlank() }
                            ?: return
                        publish {
                            it.copy(
                                narration = text,
                                streamBuffer = "",
                                info = "Narration complete",
                            )
                        }
                        return
                    }
                }
            } catch (_: Exception) {
                // fall through to plain text
            }
        }

        publish {
            val nextNarration = if (trimmed.startsWith("[WS]")) {
                it.narration
            } else {
                listOfNotNull(it.narration, trimmed).joinToString("\n")
            }
            it.copy(
                narration = nextNarration,
                info = if (trimmed.startsWith("[WS]")) trimmed else it.info,
            )
        }
    }

    private fun disconnectStomp() {
        stompRef.getAndSet(null)?.disconnect()
    }

    private fun publish(block: (UiState) -> UiState) {
        val cur = _state.value
        _state.value = block(cur)
    }

    private fun launchCall(block: suspend (UiState) -> UiState) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null)
            _state.value = try {
                withContext(Dispatchers.IO) { block(_state.value) }.copy(busy = false)
            } catch (e: Exception) {
                _state.value.copy(busy = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override fun onCleared() {
        disconnectStomp()
        super.onCleared()
    }
}

/** Loose wire model for STOMP narrative envelopes. */
data class WsEnvelope(
    val type: String? = null,
    val version: Int? = null,
    val requestId: String? = null,
    val payload: WsPayload? = null,
)

data class WsPayload(
    val chunk: String? = null,
    val text: String? = null,
    val provider: String? = null,
    val tokensUsed: Int? = null,
    val fallback: Boolean? = null,
)
