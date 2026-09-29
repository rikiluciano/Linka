package com.rikiluciano.linka.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rikiluciano.linka.LinkaApplication
import com.rikiluciano.linka.download.DownloadService
import com.rikiluciano.linka.download.DirectFileDownload
import com.rikiluciano.linka.download.DownloadFeedback
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.ExtractedMedia
import com.rikiluciano.linka.extractor.VideoExtractor
import com.rikiluciano.linka.update.AppInstaller
import com.rikiluciano.linka.update.AppRelease
import com.rikiluciano.linka.update.AppUpdateChecker
import com.rikiluciano.linka.update.AppUpdateEvents
import com.rikiluciano.linka.update.AppUpdateService
import com.rikiluciano.linka.update.AppUpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LinkaUiState(
    val address: String = "https://",
    val pageUrl: String? = null,
    val detected: Boolean = false,
    val extracting: Boolean = false,
    val updatingExtractor: Boolean = false,
    val media: ExtractedMedia? = null,
    val error: String? = null,
    val downloadFeedback: DownloadFeedback? = null,
    val appUpdate: AppUpdateState = AppUpdateState.Checking,
    val hideUpdatePrompt: Boolean = false,
)

class LinkaViewModel(application: Application) : AndroidViewModel(application) {
    private val extractor = VideoExtractor()
    private val mutableState = MutableStateFlow(LinkaUiState())
    val state: StateFlow<LinkaUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            AppUpdateEvents.state.collect { update -> mutableState.update { it.copy(appUpdate = update) } }
        }
        checkForAppUpdate()
        viewModelScope.launch {
            DownloadService.feedback.collect { feedback ->
                if (feedback != null) mutableState.update { it.copy(downloadFeedback = feedback) }
            }
        }
    }

    /** Checks the public GitHub release API without blocking the first screen. */
    fun checkForAppUpdate() {
        if (AppUpdateEvents.state.value is AppUpdateState.Downloading ||
            AppUpdateEvents.state.value is AppUpdateState.ReadyToInstall ||
            AppUpdateEvents.state.value is AppUpdateState.Installing ||
            mutableState.value.appUpdate is AppUpdateState.Downloading ||
            mutableState.value.appUpdate is AppUpdateState.ReadyToInstall ||
            mutableState.value.appUpdate is AppUpdateState.Installing
        ) return
        mutableState.update { it.copy(hideUpdatePrompt = false) }
        AppUpdateEvents.set(AppUpdateState.Checking)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { AppUpdateChecker.check(getApplication()) } }
                .onSuccess { release ->
                    AppUpdateEvents.set(release?.let(AppUpdateState::Available) ?: AppUpdateState.UpToDate)
                }
                .onFailure { error ->
                    android.util.Log.w("Linka", "No se pudo consultar el release de Linka", error)
                    AppUpdateEvents.set(AppUpdateState.Dismissed)
                }
        }
    }

    fun dismissAppUpdate() { mutableState.update { it.copy(hideUpdatePrompt = true) } }

    fun downloadAppUpdate(release: AppRelease) {
        runCatching { AppUpdateService.start(getApplication(), release) }
            .onFailure { AppUpdateEvents.set(AppUpdateState.Failed(release.version, it.message ?: "No se pudo iniciar la descarga.")) }
    }

    fun installAppUpdate(release: AppRelease, apkPath: String) {
        runCatching { AppInstaller.install(getApplication(), File(apkPath), release.version) }
            .onFailure { AppUpdateEvents.set(AppUpdateState.Failed(release.version, it.message ?: "No se pudo iniciar el instalador.")) }
    }

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
            mutableState.update { it.copy(extracting = true, updatingExtractor = true, error = null) }
            val updateError = runCatching { getApplication<LinkaApplication>().ensureYtDlpUpdated() }.exceptionOrNull()
            mutableState.update { it.copy(updatingExtractor = false) }
            runCatching { extractor.inspect(url) }
                .onSuccess { media ->
                    val warning = if (updateError != null) "No se pudo actualizar yt-dlp desde internet; se usó la versión incluida." else null
                    mutableState.update { it.copy(extracting = false, media = media, error = warning) }
                }
                .onFailure { error ->
                    mutableState.update {
                        val cause = error.message?.lineSequence()?.firstOrNull { line -> line.isNotBlank() && !line.trimStart().startsWith("WARNING:") }
                        it.copy(
                            extracting = false,
                            error = cause ?: updateError?.message?.lineSequence()?.firstOrNull() ?: "No se pudo analizar esta página.",
                        )
                    }
                }
        }
    }

    fun retryExtractFormats() {
        mutableState.update { it.copy(media = null, error = null) }
        extractFormats()
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

    fun downloadDirectFile() {
        val url = mutableState.value.pageUrl ?: return
        runCatching { DirectFileDownload.enqueue(getApplication(), url) }
            .onSuccess { name ->
                mutableState.update { it.copy(error = "Descarga iniciada: $name · revisa la notificación o Descargas/Linka.") }
            }
            .onFailure { error ->
                mutableState.update { it.copy(error = error.message ?: "No se pudo iniciar la descarga directa.") }
            }
    }

    private fun normalizeAddress(value: String): String? {
        val candidate = value.trim().let { if (it.contains("://")) it else "https://$it" }
        val uri = android.net.Uri.parse(candidate)
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return candidate
    }
}
