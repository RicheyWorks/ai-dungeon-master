package com.xai.dungeonmaster.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xai.dungeonmaster.client.apis.V2Api
import com.xai.dungeonmaster.client.models.ActionRequest
import com.xai.dungeonmaster.client.models.CatalogPayload
import com.xai.dungeonmaster.client.models.GameStatusV2
import com.xai.dungeonmaster.client.models.NarrateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges the synchronous generated SDK (jvm-okhttp4) to Compose.
 *
 * On first contact the ViewModel mints a guest session (`POST /v2/session`) and
 * attaches the JWT to every subsequent call via [HttpClients]. That gives each
 * device its own player identity — and, once the multi-player engine PR is
 * merged, its own isolated game world.
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
        val catalog: CatalogPayload? = null,
        val lastSavePath: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
        val info: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun base(): String = _state.value.baseUrl.trimEnd('/')

    private fun api(): V2Api = V2Api(basePath = base(), client = HttpClients.client())

    private fun sessions(): SessionClient = SessionClient(base(), HttpClients.client())

    fun setBaseUrl(url: String) {
        // Changing server drops the current token — a new session will mint on next ensure.
        if (url.trimEnd('/') != _state.value.baseUrl.trimEnd('/')) {
            HttpClients.clearToken()
            _state.value = _state.value.copy(baseUrl = url, session = null, status = null)
        } else {
            _state.value = _state.value.copy(baseUrl = url)
        }
    }

    /** Mint a guest session (or re-mint) and store the Bearer token. */
    fun startSession(displayName: String? = null) = launchCall { current ->
        val info = sessions().createSession(displayName)
        HttpClients.setToken(info.token)
        current.copy(
            session = info,
            error = null,
            info = "Session ${info.shortId()} · ${info.displayName}",
        )
    }

    /** Ensure a session exists, then fetch game status. */
    fun refresh() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, error = null, info = null)
    }

    fun act(choiceLabel: String) = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().submitActionV2(ActionRequest(choiceLabel))
        withSession.copy(status = envelope.payload, error = null, info = null)
    }

    fun narrate(prompt: String) = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().narrateV2(narrateRequest = NarrateRequest(prompt))
        withSession.copy(narration = envelope.payload.text, error = null, info = null)
    }

    fun loadCatalog() = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = api().getCatalogV2()
        withSession.copy(catalog = envelope.payload, error = null, info = null)
    }

    /** Enable/disable a pack; the endpoint returns the refreshed catalog. */
    fun togglePack(id: String, enable: Boolean) = launchCall { current ->
        val withSession = ensureSession(current)
        val envelope = if (enable) api().enablePackV2(id) else api().disablePackV2(id)
        withSession.copy(catalog = envelope.payload, error = null, info = null)
    }

    /**
     * Persist the caller's world. Needs server `POST /v2/save`
     * (multi-player isolation branch).
     */
    fun saveGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        val result = sessions().save(token)
        withSession.copy(
            lastSavePath = result.path,
            info = if (result.saved) "Saved${if (result.sessionScoped) " (session)" else ""}" else "Save failed",
            error = null,
        )
    }

    /** Restore from the session save file. Needs server `POST /v2/load`. */
    fun loadGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        sessions().load(token)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, info = "Loaded save", error = null)
    }

    /** Fresh party/quest for this session. Needs server `POST /v2/reset`. */
    fun resetGame() = launchCall { current ->
        val withSession = ensureSession(current)
        val token = withSession.session?.token
            ?: throw IllegalStateException("No session token")
        sessions().reset(token)
        val envelope = api().getStatusV2()
        withSession.copy(status = envelope.payload, info = "New adventure started", error = null)
    }

    private fun ensureSession(current: UiState): UiState {
        val existing = current.session
        if (existing != null && HttpClients.token() != null) {
            return current
        }
        val info = sessions().createSession(existing?.displayName)
        HttpClients.setToken(info.token)
        return current.copy(session = info)
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
}
