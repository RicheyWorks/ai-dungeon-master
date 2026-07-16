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
 * Bridges the synchronous generated SDK (jvm-okhttp4) to Compose:
 * every call runs on Dispatchers.IO and lands in a single UiState flow.
 */
class GameViewModel : ViewModel() {

    /** 10.0.2.2 is the emulator's alias for the host machine's localhost. */
    companion object {
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }

    data class UiState(
        val baseUrl: String = DEFAULT_BASE_URL,
        val status: GameStatusV2? = null,
        val narration: String? = null,
        val catalog: CatalogPayload? = null,
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun api(): V2Api = V2Api(basePath = _state.value.baseUrl.trimEnd('/'))

    fun setBaseUrl(url: String) {
        _state.value = _state.value.copy(baseUrl = url)
    }

    fun refresh() = launchCall { current ->
        val envelope = api().getStatusV2()
        current.copy(status = envelope.payload, error = null)
    }

    fun act(choiceLabel: String) = launchCall { current ->
        val envelope = api().submitActionV2(ActionRequest(choiceLabel))
        current.copy(status = envelope.payload, error = null)
    }

    fun narrate(prompt: String) = launchCall { current ->
        val envelope = api().narrateV2(narrateRequest = NarrateRequest(prompt))
        current.copy(narration = envelope.payload.text, error = null)
    }

    fun loadCatalog() = launchCall { current ->
        val envelope = api().getCatalogV2()
        current.copy(catalog = envelope.payload, error = null)
    }

    /** Enable/disable a pack; the endpoint returns the refreshed catalog. */
    fun togglePack(id: String, enable: Boolean) = launchCall { current ->
        val envelope = if (enable) api().enablePackV2(id) else api().disablePackV2(id)
        current.copy(catalog = envelope.payload, error = null)
    }

    private fun launchCall(block: suspend (UiState) -> UiState) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            _state.value = try {
                withContext(Dispatchers.IO) { block(_state.value) }.copy(busy = false)
            } catch (e: Exception) {
                _state.value.copy(busy = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }
}
