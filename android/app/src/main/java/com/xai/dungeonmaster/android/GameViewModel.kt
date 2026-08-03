package com.xai.dungeonmaster.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.xai.dungeonmaster.client.apis.HealthApi
import com.xai.dungeonmaster.client.apis.V2Api
import com.xai.dungeonmaster.client.models.ActionRequest
import com.xai.dungeonmaster.client.models.CatalogPayload
import com.xai.dungeonmaster.client.models.EntitlementPayload
import com.xai.dungeonmaster.client.models.GameStatusV2
import com.xai.dungeonmaster.client.models.HealthEnvelope
import com.xai.dungeonmaster.client.models.HealthPayload
import com.xai.dungeonmaster.client.models.NarrateRequest
import com.xai.dungeonmaster.client.models.ReadinessResponse
import com.xai.dungeonmaster.client.models.SessionRequest
import com.xai.dungeonmaster.client.models.VerifyReceiptRequest
import com.xai.dungeonmaster.client.infrastructure.ClientException
import okhttp3.Request
import java.io.File
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
 * STOMP socket (`/ws-stomp`) for live narration. Session + server URL are
 * restored from [SessionStore] across process restarts.
 */
class GameViewModel(
    private val store: SessionStore,
) : ViewModel() {

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
        val marketplace: MarketplacePayload? = null,
        val marketQuery: String = "",
        val installJob: MarketplaceInstallJob? = null,
        val entitlements: EntitlementPayload? = null,
        val readiness: ReadinessResponse? = null,
        val health: HealthPayload? = null,
        val healthOk: Boolean? = null,
        val healthError: String? = null,
        val healthAtEpochMs: Long? = null,
        val lastSavePath: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
    )

    private val _state = MutableStateFlow(restoreInitialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val stompRef = AtomicReference<StompClient?>(null)
    private val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val envelopeAdapter = moshi.adapter(WsEnvelope::class.java)

    private fun restoreInitialState(): UiState {
        val url = store.loadBaseUrl(DEFAULT_BASE_URL)
        val saved = store.loadSession()
        if (saved != null && !saved.isExpired()) {
            HttpClients.setToken(saved.token)
            return UiState(
                baseUrl = url,
                session = saved,
                info = "Restored session ${saved.shortId()} · ${saved.displayName}",
            )
        }
        if (saved != null) {
            store.clearSession()
        }
        HttpClients.clearToken()
        return UiState(baseUrl = url)
    }

    private fun base(): String = _state.value.baseUrl.trimEnd('/')

    private fun api(): V2Api = V2Api(basePath = base(), client = HttpClients.client())

    private fun healthApi(): HealthApi = HealthApi(basePath = base(), client = HttpClients.client())

    /** Public probes — no session required. Soft-handles 503 readiness bodies. */
    fun pollHealth() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { fetchHealthSnapshot(base()) }
            publish {
                it.copy(
                    readiness = snapshot.readiness,
                    health = snapshot.health,
                    healthOk = snapshot.ok,
                    healthError = snapshot.error,
                    healthAtEpochMs = System.currentTimeMillis(),
                )
            }
        }
    }

    private data class HealthSnapshot(
        val readiness: ReadinessResponse?,
        val health: HealthPayload?,
        val ok: Boolean,
        val error: String?,
    )

    private fun fetchHealthSnapshot(baseUrl: String): HealthSnapshot {
        val client = HttpClients.client()
        val readinessAdapter = moshi.adapter(ReadinessResponse::class.java)
        val healthEnvAdapter = moshi.adapter(HealthEnvelope::class.java)
        var readiness: ReadinessResponse? = null
        var health: HealthPayload? = null
        var error: String? = null
        var readyOk = false
        var healthOk = false

        try {
            client.newCall(
                Request.Builder().url("$baseUrl/health/ready").header("Accept", "application/json").get().build(),
            ).execute().use { res ->
                val body = res.body?.string().orEmpty()
                readiness = if (body.isNotBlank()) readinessAdapter.fromJson(body) else null
                readyOk = res.isSuccessful
                if (!readyOk && readiness == null) {
                    error = "readiness HTTP ${res.code}"
                }
            }
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }

        try {
            client.newCall(
                Request.Builder().url("$baseUrl/v2/health").header("Accept", "application/json").get().build(),
            ).execute().use { res ->
                val body = res.body?.string().orEmpty()
                val env = if (body.isNotBlank()) healthEnvAdapter.fromJson(body) else null
                health = env?.payload
                healthOk = res.isSuccessful
                if (!healthOk && health == null && error == null) {
                    error = "health HTTP ${res.code}"
                }
            }
        } catch (e: Exception) {
            if (error == null) error = e.message ?: e.javaClass.simpleName
        }

        // Prefer SDK for liveness-only warm path when soft parse failed
        if (readiness == null && health == null) {
            try {
                healthApi().getLiveness()
            } catch (_: Exception) {
                /* ignore */
            }
        }

        return HealthSnapshot(
            readiness = readiness,
            health = health,
            ok = readyOk && healthOk,
            error = error,
        )
    }

    fun setBaseUrl(url: String) {
        store.saveBaseUrl(url)
        if (url.trimEnd('/') != _state.value.baseUrl.trimEnd('/')) {
            disconnectStomp()
            HttpClients.clearToken()
            store.clearSession()
            _state.value = _state.value.copy(
                baseUrl = url,
                session = null,
                status = null,
                stompConnected = false,
                info = "Server changed — new session on next sync",
            )
            pollHealth()
        } else {
            _state.value = _state.value.copy(baseUrl = url)
        }
    }

    /** Mint a guest session (or re-mint) via generated SDK and open STOMP. */
    fun startSession(displayName: String? = null) = launchCall { current ->
        disconnectStomp()
        store.clearSession()
        val info = mintSession(displayName)
        HttpClients.setToken(info.token)
        store.saveSession(info)
        connectStomp(info)
        current.copy(
            session = info,
            error = null,
            info = "Session ${info.shortId()} · ${info.displayName}",
        )
    }

    /**
     * Explicit logout: tell the server to drop identity/packs/engine, then
     * wipe local session state.
     */
    fun logout() = launchCall { current ->
        disconnectStomp()
        val token = current.session?.token ?: HttpClients.token()
        if (!token.isNullOrBlank()) {
            try {
                // Ensure interceptor has the token for generated SDK call.
                HttpClients.setToken(token)
                api().deleteSessionV2()
            } catch (e: ClientException) {
                if (e.statusCode != 401) {
                    // Still clear local state; surface non-auth failures as info.
                    // fall through
                }
            } catch (_: Exception) {
                // Still clear local state if the server already forgot us.
            }
        }
        HttpClients.clearToken()
        store.clearSession()
        current.copy(
            session = null,
            status = null,
            catalog = null,
            entitlements = null,
            stompConnected = false,
            error = null,
            info = "Logged out",
        )
    }

    /** Ensure a session exists, then fetch game status. */
    fun refresh() = launchCall { current ->
        val withSession = ensureSession(current)
        connectStomp(withSession.session!!)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, error = null, info = withSession.info)
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

    fun setMarketQuery(q: String) {
        publish { it.copy(marketQuery = q) }
    }

    fun loadMarketplace(query: String? = null) {
        viewModelScope.launch {
            val q = query ?: _state.value.marketQuery
            try {
                val payload = withContext(Dispatchers.IO) {
                    api().listMarketplaceV2(q = q.ifBlank { null }).payload.toUi()
                }
                publish {
                    it.copy(
                        marketplace = payload,
                        marketQuery = q,
                        error = null,
                        info = "Marketplace: ${payload.available ?: 0} available",
                    )
                }
            } catch (e: Exception) {
                publish { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun installMarketplacePack(id: String) {
        viewModelScope.launch {
            try {
                val started = withContext(Dispatchers.IO) { postInstallAsync(id) }
                publish { it.copy(installJob = started, info = "Installing $id…", error = null) }
                val done = withContext(Dispatchers.IO) {
                    pollInstallJob(started.jobId) { job ->
                        publish { it.copy(installJob = job) }
                    }
                }
                publish { it.copy(installJob = done) }
                when (done.phase) {
                    "DONE" -> {
                        val payload = withContext(Dispatchers.IO) {
                            api().listMarketplaceV2(q = _state.value.marketQuery.ifBlank { null })
                                .payload.toUi()
                        }
                        val catalog = try {
                            withContext(Dispatchers.IO) {
                                ensureSession(_state.value)
                                api().getCatalogV2().payload
                            }
                        } catch (_: Exception) {
                            _state.value.catalog
                        }
                        publish {
                            it.copy(
                                marketplace = payload,
                                catalog = catalog,
                                info = done.message ?: "Installed $id",
                                error = null,
                            )
                        }
                    }
                    "CANCELLED" -> publish { it.copy(info = done.message ?: "Install cancelled") }
                    else -> publish {
                        it.copy(error = done.error ?: done.message ?: "Install failed")
                    }
                }
            } catch (e: Exception) {
                publish { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun cancelMarketplaceInstall() {
        val jobId = _state.value.installJob?.jobId ?: return
        viewModelScope.launch {
            try {
                val j = withContext(Dispatchers.IO) {
                    api().cancelMarketplaceInstallJobV2(jobId).payload.toUi()
                }
                publish { it.copy(installJob = j, info = "Cancel requested") }
            } catch (e: Exception) {
                publish { it.copy(error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /**
     * Async install returns HTTP 202 + job envelope; generated install method is typed
     * for the sync [MarketplaceInstallEnvelope]. Decode the job body with Moshi.
     */
    private fun postInstallAsync(id: String): MarketplaceInstallJob {
        val req = okhttp3.Request.Builder()
            .url("$base()/v2/marketplace/${java.net.URLEncoder.encode(id, "UTF-8")}/install?async=true")
            .header("Accept", "application/json")
            .post(okhttp3.RequestBody.create(ByteArray(0), null))
            .build()
        HttpClients.client().newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val err = try {
                    com.xai.dungeonmaster.client.infrastructure.Serializer.moshi
                        .adapter(com.xai.dungeonmaster.client.models.ErrorEnvelope::class.java)
                        .fromJson(body)?.payload?.message
                } catch (_: Exception) {
                    null
                }
                throw IllegalStateException(err ?: "install async HTTP ${res.code}")
            }
            val env = com.xai.dungeonmaster.client.infrastructure.Serializer.moshi
                .adapter(com.xai.dungeonmaster.client.models.MarketplaceInstallJobEnvelope::class.java)
                .fromJson(body)
            val job = env?.payload ?: throw IllegalStateException("missing job payload")
            return job.toUi()

        }
    }

    private fun getInstallJob(jobId: String): MarketplaceInstallJob {
        return api().getMarketplaceInstallJobV2(jobId).payload.toUi()
    }

    private suspend fun pollInstallJob(
        jobId: String,
        onProgress: (MarketplaceInstallJob) -> Unit,
    ): MarketplaceInstallJob {
        val deadline = System.currentTimeMillis() + 120_000
        var last: MarketplaceInstallJob? = null
        while (System.currentTimeMillis() < deadline) {
            last = withContext(Dispatchers.IO) { getInstallJob(jobId) }
            withContext(Dispatchers.Main) { onProgress(last!!) }
            when (last.phase) {
                "DONE", "FAILED", "CANCELLED" -> return last
            }
            kotlinx.coroutines.delay(400)
        }
        throw IllegalStateException("install timed out")
    }

    fun togglePack(id: String, enable: Boolean) = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = if (enable) api().enablePackV2(id) else api().disablePackV2(id)
        withSession.copy(catalog = envelope.payload, error = null, info = null)
    }

    /**
     * Upload a content-pack zip via multipart `POST /v2/catalog/packs`.
     * [replace] overwrites an existing pack with the same id (else 409).
     * The temp [file] is deleted after the request when it lives under cacheDir.
     */
    fun uploadPack(file: File, replace: Boolean = false) = launchCall { current ->
        val withSession = ensureSession(current)
        try {
            val envelope = api().uploadPackV2(file = file, replace = replace)
            withSession.copy(
                catalog = envelope.payload,
                info = if (replace) "Pack replaced: ${file.name}" else "Pack uploaded: ${file.name}",
                error = null,
            )
        } catch (e: ClientException) {
            withSession.copy(
                error = "Upload failed (${e.statusCode}): ${e.message}",
                info = null,
            )
        } finally {
            if (file.name.startsWith("upload-") && file.extension == "zip") {
                file.delete()
            }
        }
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
            val packs = p.enabledPacks.orEmpty()
            val infoMsg = when {
                p.granted != true -> "Not granted: ${p.reason}"
                packs.isNotEmpty() -> "Granted ${p.productId}; enabled packs: ${packs.joinToString(", ")}"
                else -> "Granted ${p.productId}"
            }
            val catalog = if (p.granted == true && packs.isNotEmpty()) {
                try {
                    api().getCatalogV2().payload
                } catch (_: Exception) {
                    withSession.catalog
                }
            } else {
                withSession.catalog
            }
            withSession.copy(
                entitlements = p,
                catalog = catalog,
                info = infoMsg,
                error = null,
            )
        } catch (e: ClientException) {
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

    /** Mint a sandbox receipt for [storefront] and verify it (dev / google_play / app_store). */
    fun sandboxPurchase(productId: String, storefront: String = DevReceipts.STOREFRONT_DEV) = launchCall { current ->
        val withSession = ensureSession(current)
        val minted = DevReceipts.mint(storefront, productId)
        try {
            val envelope = api().verifyReceiptV2(
                VerifyReceiptRequest(
                    productId = minted.productId,
                    receipt = minted.receipt,
                    storefront = minted.storefront,
                ),
            )
            val p = envelope.payload
            val packs = p.enabledPacks.orEmpty()
            val infoMsg = when {
                p.granted != true -> "Sandbox purchase failed: ${p.reason}"
                packs.isNotEmpty() ->
                    "Sandbox ${minted.storefront} granted ${p.productId}; enabled packs: ${packs.joinToString(", ")}"
                else -> "Sandbox ${minted.storefront} granted: ${p.productId}"
            }
            val catalog = if (p.granted == true && packs.isNotEmpty()) {
                try {
                    api().getCatalogV2().payload
                } catch (_: Exception) {
                    withSession.catalog
                }
            } else {
                withSession.catalog
            }
            withSession.copy(
                entitlements = p,
                catalog = catalog,
                info = infoMsg,
                error = null,
            )
        } catch (e: ClientException) {
            withSession.copy(
                error = "Sandbox purchase failed (${e.statusCode}): ${e.message}",
                info = null,
            )
        }
    }

    /** @deprecated prefer [sandboxPurchase] */
    fun devPurchase(productId: String) = sandboxPurchase(productId, DevReceipts.STOREFRONT_DEV)

    fun saveGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().saveGameV2()
        val p = envelope.payload
        withSession.copy(
            lastSavePath = p?.path,
            info = if (p?.saved == true) {
                "Saved${if (p.sessionScoped == true) " (session)" else ""}"
            } else {
                "Save failed"
            },
            error = null,
        )
    }

    fun loadGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().loadGameV2()
        withSession.copy(status = envelope.payload, info = "Loaded save", error = null)
    }

    fun resetGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().resetGameV2()
        withSession.copy(status = envelope.payload, info = "New adventure started", error = null)
    }


    private fun mintSession(displayName: String?): SessionInfo {
        val req = displayName?.takeIf { it.isNotBlank() }?.let { SessionRequest(displayName = it) }
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

    /**
     * Prefer in-memory session, then disk. Validate with `/v2/session/me`; on
     * failure mint a fresh guest session and persist it.
     */
    private fun ensureSession(current: UiState): UiState {
        var candidate = current.session
        if (candidate == null || HttpClients.token() == null) {
            val fromDisk = store.loadSession()
            if (fromDisk != null && !fromDisk.isExpired()) {
                candidate = fromDisk
                HttpClients.setToken(fromDisk.token)
            }
        }

        if (candidate != null && !candidate.isExpired() && HttpClients.token() != null) {
            try {
                api().getSessionMeV2()
                store.saveSession(candidate)
                return current.copy(session = candidate)
            } catch (_: Exception) {
                // Stale JWT — fall through to mint.
            }
        }

        store.clearSession()
        val info = mintSession(candidate?.displayName)
        HttpClients.setToken(info.token)
        store.saveSession(info)
        return current.copy(session = info, info = "New session ${info.shortId()}")
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

    class Factory(private val store: SessionStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                return GameViewModel(store) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
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
