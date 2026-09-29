package com.rikiluciano.linka.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rikiluciano.linka.LinkaApplication
import com.rikiluciano.linka.download.DownloadService
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.ExtractedMedia
import com.rikiluciano.linka.extractor.VideoExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LinkaUiState(
    val address: String = "https://",
    val pageUrl: String? = null,
    val detected: Boolean = false,
    val extracting: Boolean = false,
    val media: ExtractedMedia? = null,
    val error: String? = null,
)

class LinkaViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = VideoExtractor()
    private val mutableState = MutableStateFlow(LinkaUiState())
    val state: StateFlow<LinkaUiState> = mutableState.asStateFlow()

    fun editAddress(value: String) = mutableState.update { it.copy(address = value, error = null) }

    fun navigate() {
        val url = normalizeAddress(mutableState.value.address)
        if (url == null) {
            mutableState.update { it.copy(error = "Escribe una dirección web válida (http o https).") }
            return
        }
        mutableState.update { it.copy(address = url, pageUrl = url, detected = false, media = null, error = null) }
    }

    fun openSharedLink(raw: String) {
        editAddress(raw.trim())
        navigate()
    }

    fun pageChanged(url: String?) {
        if (url.isNullOrBlank()) return
        mutableState.update { it.copy(address = url, pageUrl = url, detected = false, media = null, error = null) }
    }

    fun videoDetected(pageUrl: String?) {
        if (pageUrl.isNullOrBlank()) return
        mutableState.update { it.copy(pageUrl = pageUrl, address = pageUrl, detected = true, error = null) }
    }

    fun extractFormats() {
        val current = mutableState.value
        if (current.extracting || current.media != null) return
        val url = current.pageUrl ?: return
        if (!(getApplication<LinkaApplication>().extractorReady)) {
            mutableState.update { it.copy(error = "El motor multimedia no pudo inicializarse en este dispositivo.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(extracting = true, error = null) }
            runCatching { extractor.inspect(url) }
                .onSuccess { media -> mutableState.update { it.copy(extracting = false, media = media) } }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(extracting = false, error = error.message?.lineSequence()?.firstOrNull() ?: "No se pudo analizar esta página.")
                    }
                }
        }
    }

    fun download(quality: DownloadQuality) {
        val current = mutableState.value
        val url = current.pageUrl ?: return
        val app = getApplication<LinkaApplication>()
        if (!app.extractorReady) {
            mutableState.update { it.copy(error = "El motor multimedia no está listo.") }
            return
        }
        DownloadService.start(app, url, current.media?.title ?: "Descarga de Linka", quality)
        mutableState.update { it.copy(media = null, detected = false, error = "Descarga iniciada. Sigue su progreso en la notificación.") }
    }

    private fun normalizeAddress(value: String): String? {
        val candidate = value.trim().let { if (it.contains("://")) it else "https://$it" }
        val uri = android.net.Uri.parse(candidate)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return candidate
    }
}
