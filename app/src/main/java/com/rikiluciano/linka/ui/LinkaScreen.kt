package com.rikiluciano.linka.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.MediaFormat

private val LinkaColors = darkColorScheme(
    primary = Color(0xFF8877FF),
    onPrimary = Color.White,
    background = Color(0xFF11131A),
    surface = Color(0xFF1B1F2A),
    onSurface = Color(0xFFF1F2F7),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkaScreen(model: LinkaViewModel) {
    val state by model.state.collectAsState()
    var showQualities by remember { mutableStateOf(false) }
    var pendingQuality by remember { mutableStateOf<DownloadQuality?>(null) }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingQuality?.let(model::download)
        pendingQuality = null
        showQualities = false
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
                        if (state.extracting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                        state.error?.let { Text(it, Modifier.padding(top = 6.dp), color = Color(0xFFFFB4AB), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            },
            floatingActionButton = {
                if (state.detected) {
                    FloatingActionButton(onClick = {
                        showQualities = true
                        model.extractFormats()
                    }) { Text("↓") }
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
                    Text(
                        "Navega hasta un video. Linka detectará medios compatibles y te permitirá elegir la descarga.",
                        Modifier.align(Alignment.Center).padding(28.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (showQualities) {
            ModalBottomSheet(onDismissRequest = { showQualities = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Descargar contenido autorizado", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "La detección no garantiza que todas las webs sean compatibles. Linka no intenta eludir DRM ni iniciar sesión en tu nombre.",
                        Modifier.padding(top = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.extracting) {
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                    } else {
                        Button(onClick = { startDownload(DownloadQuality.Best) }, Modifier.fillMaxWidth()) {
                            Text("Mejor calidad disponible")
                        }
                        TextButton(onClick = { startDownload(DownloadQuality.AudioMp3) }) {
                            Text("Extraer audio MP3 · máxima calidad")
                        }
                        state.media?.let { media ->
                            Text(media.title, Modifier.padding(vertical = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            LazyColumn {
                                items(media.formats, key = MediaFormat::id) { format ->
                                    TextButton(
                                        onClick = { startDownload(DownloadQuality.Format(format)) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(format.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        if (state.media?.formats.isNullOrEmpty() && !state.extracting) {
                            Text("No se listaron formatos. Puedes intentar la mejor calidad disponible.", Modifier.padding(vertical = 12.dp))
                        }
                    }
                }
            }
        }

        state.error?.let { message ->
            // Errors remain in the address bar for screen readers and are cleared on navigation.
        }
    }
}
