package com.rikiluciano.linka.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.MediaFormat
import com.rikiluciano.linka.update.AppUpdateState

private val LinkaColors = darkColorScheme(
    primary = Color(0xFF8877FF),
    onPrimary = Color.White,
    background = Color(0xFF11131A),
    surface = Color(0xFF1B1F2A),
    onSurface = Color(0xFFF1F2F7),
)

private enum class DownloadMode { Video, Audio }

private fun qualityLabel(height: Int): String = when (height) {
    144 -> "144p · mínima"
    240 -> "240p · baja"
    360 -> "360p · estándar"
    480 -> "480p · SD"
    720 -> "720p · HD"
    1080 -> "1080p · Full HD"
    1440 -> "1440p · QHD / 2K"
    2160 -> "2160p · 4K UHD"
    4320 -> "4320p · 8K UHD"
    else -> "${height}p · fuente"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkaScreen(model: LinkaViewModel) {
    val state by model.state.collectAsState()
    var showQualities by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf<DownloadQuality?>(null) }
    var downloadMode by remember { mutableStateOf(DownloadMode.Video) }
    var selectedVideoFormat by remember { mutableStateOf<MediaFormat?>(null) }
    val context = LocalContext.current
    fun pasteLink() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val value = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (value.isNullOrBlank()) {
            Toast.makeText(context, "No hay un enlace en el portapapeles", Toast.LENGTH_SHORT).show()
        } else {
            model.openSharedLink(value)
        }
    }
    var permissionSettingsReturn by remember { mutableIntStateOf(0) }
    val installerAllowed = remember(permissionSettingsReturn) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }
    val installerPermissionSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        permissionSettingsReturn++
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingQuality?.let(model::download)
        pendingQuality = null
        showQualities = false
    }
    LaunchedEffect(state.media) {
        val media = state.media
        val videoOptions = media?.videoQualities().orEmpty()
        selectedVideoFormat = videoOptions.firstOrNull()
        downloadMode = if (videoOptions.isEmpty() && media?.hasAudioSource() == true) DownloadMode.Audio else DownloadMode.Video
    }
    fun startDownload(quality: DownloadQuality) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingQuality = quality
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.download(quality)
            showQualities = false
        }
    }

    MaterialTheme(colorScheme = LinkaColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text("Linka", style = MaterialTheme.typography.titleLarge, color = LinkaColors.primary)
                        Text(
                            "Navega y guarda contenido que tengas permiso para descargar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.address,
                                onValueChange = model::editAddress,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Dirección web") },
                            )
                            Button(onClick = model::navigate) { Text("Ir") }
                        }
                        TextButton(onClick = ::pasteLink, modifier = Modifier.padding(top = 2.dp)) { Text("Pegar enlace del portapapeles") }
                        if (state.extracting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                        state.error?.let { Text(it, Modifier.padding(top = 6.dp), color = Color(0xFFFFB4AB), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 5.dp,
                    shadowElevation = 10.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = when {
                                state.pageUrl == null -> "Pega un enlace o navega hasta un video"
                                state.detected -> "Video detectado · toca para elegir calidad"
                                else -> "¿Hay un video en esta página? Analiza el enlace"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        state.downloadFeedback?.let { feedback ->
                            Text(
                                feedback.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (feedback.isError) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Button(
                            onClick = {
                                showQualities = true
                                model.extractFormats()
                            },
                            enabled = state.pageUrl != null && !state.extracting,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                when {
                                    state.updatingExtractor -> "Actualizando motor…"
                                    state.extracting -> "Analizando página…"
                                    else -> "Buscar opciones de descarga  ↓"
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                BrowserView(
                    url = state.pageUrl,
                    modifier = Modifier.fillMaxSize(),
                    onPageChanged = model::pageChanged,
                    onVideoDetected = model::videoDetected,
                )
                if (state.pageUrl == null) {
                    Surface(
                        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 22.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Tu enlace. Tu elección.", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Abre una página o pega un enlace. Linka analizará las calidades que realmente ofrezca el video y te dejará elegir video o MP3.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                            )
                            Button(onClick = ::pasteLink, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Text("Pegar enlace")
                            }
                        }
                    }
                }
            }
        }

        if (showQualities) {
            ModalBottomSheet(onDismissRequest = { showQualities = false }) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Opciones de descarga", style = MaterialTheme.typography.titleLarge)
                    state.media?.let { media ->
                        Text(media.title, Modifier.padding(top = 4.dp, bottom = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.extracting) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(if (state.updatingExtractor) "Preparando el análisis…" else "Buscando calidades reales disponibles…")
                        }
                    }
                    state.media?.let { media ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                            FilterChip(
                                selected = downloadMode == DownloadMode.Video,
                                onClick = { downloadMode = DownloadMode.Video },
                                enabled = media.videoQualities().isNotEmpty(),
                                label = { Text("Video") },
                            )
                            FilterChip(
                                selected = downloadMode == DownloadMode.Audio,
                                onClick = { downloadMode = DownloadMode.Audio },
                                enabled = media.hasAudioSource(),
                                label = { Text("Audio MP3") },
                            )
                        }
                        HorizontalDivider()
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            when (downloadMode) {
                                DownloadMode.Video -> {
                                    val options = media.videoQualities()
                                    if (options.isEmpty()) {
                                        item {
                                            Text(
                                                "Este enlace no ofrece una pista de video compatible.",
                                                Modifier.padding(vertical = 18.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    } else items(options, key = MediaFormat::id) { format ->
                                        val isSelected = selectedVideoFormat?.id == format.id
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { selectedVideoFormat = format }.padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(qualityLabel(format.height), style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    if (format.height == options.first().height) "Máxima resolución que ofrece esta fuente"
                                                    else "Resolución disponible en el archivo original",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                )
                                                if (format.needsMerge) Text(
                                                    "Linka unirá el audio compatible automáticamente",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                )
                                            }
                                            RadioButton(selected = isSelected, onClick = { selectedVideoFormat = format })
                                        }
                                        HorizontalDivider()
                                    }
                                }
                                DownloadMode.Audio -> item {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                                        Text("MP3 · máxima calidad de conversión", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Se conserva la mejor pista de audio disponible y se convierte con FFmpeg en MP3 V0. La conversión no inventa detalle que no esté en la fuente.",
                                            Modifier.padding(top = 6.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                        )
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = {
                                when (downloadMode) {
                                    DownloadMode.Video -> selectedVideoFormat?.let { startDownload(DownloadQuality.Format(it)) }
                                    DownloadMode.Audio -> startDownload(DownloadQuality.AudioMp3)
                                }
                            },
                            enabled = !state.extracting && when (downloadMode) {
                                DownloadMode.Video -> selectedVideoFormat != null
                                DownloadMode.Audio -> media.hasAudioSource()
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).padding(bottom = 4.dp),
                        ) {
                            Text(
                                when (downloadMode) {
                                    DownloadMode.Video -> selectedVideoFormat?.let { "Descargar ${qualityLabel(it.height)}" } ?: "Video no disponible"
                                    DownloadMode.Audio -> "Descargar MP3 · alta calidad"
                                },
                            )
                        }
                    }
                    if (state.media == null && !state.extracting) {
                        Text(
                            "Aún no se han detectado las opciones de este enlace.",
                            Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.pageUrl != null) TextButton(onClick = model::extractFormats) { Text("Analizar enlace") }
                    }
                    state.error?.let {
                        Text(it, Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.media != null && state.media?.formats.isNullOrEmpty() && !state.extracting) {
                        Text(
                            "No se encontraron pistas multimedia utilizables. Comprueba el enlace o vuelve a intentarlo.",
                            Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = model::retryExtractFormats) { Text("Reintentar análisis") }
                    }
                }
            }
        }

        if (!state.hideUpdatePrompt) when (val update = state.appUpdate) {
            is AppUpdateState.Available -> AlertDialog(
                onDismissRequest = model::dismissAppUpdate,
                title = { Text("Nueva versión de Linka") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("La versión ${update.release.version} está disponible. ¿Quieres actualizar ahora?")
                        if (update.release.sizeBytes > 0) Text(
                            "Descarga aproximada: ${"%.1f".format(update.release.sizeBytes / 1_048_576.0)} MB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        update.release.notes.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                        }
                        Text("El APK se descargará de forma privada en segundo plano.", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { Button(onClick = { model.downloadAppUpdate(update.release) }) { Text("Actualizar") } },
                dismissButton = { TextButton(onClick = model::dismissAppUpdate) { Text("Ahora no") } },
            )
            is AppUpdateState.Downloading -> AlertDialog(
                onDismissRequest = model::dismissAppUpdate,
                title = { Text("Descargando Linka ${update.release.version}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(progress = { update.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${update.progress}% · Puedes seguir usando Linka mientras se descarga.")
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = model::dismissAppUpdate) { Text("Seguir en segundo plano") } },
            )
            is AppUpdateState.ReadyToInstall -> AlertDialog(
                onDismissRequest = model::dismissAppUpdate,
                title = { Text("Actualización lista") },
                text = { Text("Linka ${update.release.version} se descargó y verificó. Pulsa continuar para abrir el instalador de Android.") },
                confirmButton = {
                    Button(onClick = {
                        if (!installerAllowed) {
                            installerPermissionSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                        } else {
                            model.installAppUpdate(update.release, update.apkPath)
                        }
                    }) { Text(if (!installerAllowed) "Permitir instalación" else "Continuar") }
                },
                dismissButton = { TextButton(onClick = model::dismissAppUpdate) { Text("Más tarde") } },
            )
            is AppUpdateState.Installing -> AlertDialog(
                onDismissRequest = {},
                title = { Text("Confirma en Android") },
                text = { Text("El instalador del sistema está esperando tu confirmación para reemplazar Linka por la versión ${update.version}.") },
                confirmButton = {},
            )
            is AppUpdateState.Failed -> AlertDialog(
                onDismissRequest = model::dismissAppUpdate,
                title = { Text(if (update.version == null) "No se pudo comprobar actualizaciones" else "No se pudo actualizar Linka") },
                text = { Text(update.message) },
                confirmButton = {
                    TextButton(onClick = {
                        if (update.version == null) model.checkForAppUpdate() else model.dismissAppUpdate()
                    }) { Text(if (update.version == null) "Reintentar" else "Cerrar") }
                },
                dismissButton = if (update.version == null) ({ TextButton(onClick = model::dismissAppUpdate) { Text("Cerrar") } }) else null,
            )
            AppUpdateState.Checking, AppUpdateState.UpToDate, AppUpdateState.Dismissed -> Unit
        }
    }
}
