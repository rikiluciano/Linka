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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.MediaFormat
import com.rikiluciano.linka.update.AppUpdateState

private val LinkaColors = darkColorScheme(
    primary = Color(0xFF9B8CFF),
    onPrimary = Color.White,
    secondary = Color(0xFF65DEC0),
    onSecondary = Color(0xFF06241D),
    background = Color(0xFF0C0F16),
    surface = Color(0xFF151A25),
    surfaceVariant = Color(0xFF202737),
    onSurface = Color(0xFFF3F4FA),
    onSurfaceVariant = Color(0xFFB4BAC9),
    outline = Color(0xFF394255),
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
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(15.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("L", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Linka", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(
                                    "Navega · elige · guarda",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("SIN ANUNCIOS", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = state.address,
                                onValueChange = model::editAddress,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Enlace o dirección web") },
                                shape = RoundedCornerShape(18.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                            )
                            Button(
                                onClick = model::navigate,
                                modifier = Modifier.height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                            ) { Text("Abrir", fontWeight = FontWeight.SemiBold) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::pasteLink, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                                Text("Pegar enlace", fontWeight = FontWeight.SemiBold)
                            }
                            Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Solo contenido que tengas permiso para guardar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.extracting) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary)
                        state.error?.let { Text(it, Modifier.fillMaxWidth(), color = Color(0xFFFFB4AB), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 5.dp,
                    shadowElevation = 10.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                modifier = Modifier.size(9.dp),
                                shape = RoundedCornerShape(50),
                                color = if (state.detected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            ) { }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        state.pageUrl == null -> "Listo para explorar"
                                        state.detected -> "Contenido multimedia detectado"
                                        else -> "¿Hay un video en esta página?"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = when {
                                        state.pageUrl == null -> "Pega un enlace o abre un sitio web"
                                        state.detected -> "Analiza el enlace y elige cómo guardarlo"
                                        else -> "Busca los formatos disponibles en la fuente"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
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
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Text(
                                when {
                                    state.updatingExtractor -> "Actualizando motor…"
                                    state.extracting -> "Analizando página…"
                                    else -> "Elegir descarga   ↓"
                                },
                                fontWeight = FontWeight.SemiBold,
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
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("VIDEO · AUDIO · A TU MANERA", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Tu enlace. Tu elección.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(
                                "Explora una página o pega un enlace. Linka te mostrará las calidades reales del video y te permitirá guardar solo el audio en MP3.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("480p", "HD", "4K", "MP3").forEach { label ->
                                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(label, Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            Button(onClick = ::pasteLink, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp)) {
                                Text("Pegar un enlace", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        if (showQualities) {
            ModalBottomSheet(
                onDismissRequest = { showQualities = false },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().fillMaxHeight(0.84f).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Tu descarga", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Elige formato y calidad", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp))
                    state.media?.let { media ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("VISTA PREVIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                Text(media.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                state.pageUrl?.let { raw ->
                                    val host = runCatching { Uri.parse(raw).host?.removePrefix("www.") }.getOrNull()
                                    if (!host.isNullOrBlank()) Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            FilterChip(
                                selected = downloadMode == DownloadMode.Video,
                                onClick = { downloadMode = DownloadMode.Video },
                                enabled = media.videoQualities().isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                label = { Text("Video") },
                            )
                            FilterChip(
                                selected = downloadMode == DownloadMode.Audio,
                                onClick = { downloadMode = DownloadMode.Audio },
                                enabled = media.hasAudioSource(),
                                modifier = Modifier.weight(1f),
                                label = { Text("Audio MP3") },
                            )
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
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
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedVideoFormat = format },
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        ) {
                                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Text(qualityLabel(format.height), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                                        if (format.height == options.first().height) Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)) {
                                                            Text("MÁXIMA", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Text(
                                                        if (format.height == options.first().height) "Mayor resolución disponible en la fuente"
                                                        else "Resolución original disponible",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                    if (format.needsMerge) Text(
                                                        "El audio se combinará automáticamente",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                RadioButton(selected = isSelected, onClick = { selectedVideoFormat = format })
                                            }
                                        }
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
                            modifier = Modifier.fillMaxWidth().height(54.dp).padding(top = 8.dp, bottom = 12.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Text(
                                when (downloadMode) {
                                    DownloadMode.Video -> selectedVideoFormat?.let { "Descargar video · ${qualityLabel(it.height)}" } ?: "Video no disponible"
                                    DownloadMode.Audio -> "Descargar audio · MP3"
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
