package com.rikiluciano.linka.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.rikiluciano.linka.R
import com.rikiluciano.linka.LinkaApplication
import com.rikiluciano.linka.extractor.DownloadQuality
import com.rikiluciano.linka.extractor.MediaFormat
import com.rikiluciano.linka.extractor.VideoExtractor
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.UUID
import kotlin.math.max

data class DownloadFeedback(val message: String, val isError: Boolean = false)

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val extractor = VideoExtractor()
    private val notificationId = 4201

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val url = intent.getStringExtra(EXTRA_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Descarga de Linka"
        val formatId = intent.getStringExtra(EXTRA_FORMAT_ID)
        val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
        val hasAudio = intent.getBooleanExtra(EXTRA_HAS_AUDIO, false)
        if (url.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        feedback.value = DownloadFeedback("Preparando descarga…")
        startAsForeground(notification("Preparando descarga", 0, "Iniciando…", true))
        scope.launch {
            val outputDir = File(cacheDir, "linka-downloads")
                .apply { mkdirs() }
            val quality = when {
                audioOnly -> DownloadQuality.AudioMp3
                formatId != null -> DownloadQuality.Format(
                    MediaFormat(formatId, title, 0, "mp4", true, hasAudio, 0.0)
                )
                else -> DownloadQuality.Best
            }
            val before = outputDir.listFiles().orEmpty().map(File::getName).toSet()
            val startedAt = System.currentTimeMillis()
            val template = File(outputDir, "%(title).160B [%(id)s].%(ext)s").absolutePath
            val request = extractor.buildRequest(url, quality, template)
            val processId = "linka-${UUID.randomUUID()}"
            var progress = 0f
            var lastBytes = outputDir.walkTopDown().filter(File::isFile).sumOf(File::length)
            var lastTime = System.currentTimeMillis()
            val speedMonitor = launch {
                while (isActive) {
                    delay(1000)
                    val now = System.currentTimeMillis()
                    val bytes = outputDir.walkTopDown().filter(File::isFile).sumOf { it.length() }
                    val speed = max(0L, bytes - lastBytes) * 1000 / max(1L, now - lastTime) / 1024
                    lastBytes = bytes
                    lastTime = now
                    notificationManager().notify(
                        notificationId,
                            notification(
                                title,
                                progress.toInt(),
                                if (progress >= 99.9f) "Finalizando y uniendo pistas · ${speed} kB/s" else "${speed} kB/s · Descargando",
                                true,
                            ),
                    )
                }
            }
            try {
                runCatching { (application as LinkaApplication).ensureYtDlpUpdated() }
                    .onFailure { android.util.Log.w("Linka", "No se pudo actualizar yt-dlp; se probará el motor incluido.", it) }
                feedback.value = DownloadFeedback("Descarga en curso; el progreso aparece en la notificación.")
                withContext(Dispatchers.IO) {
                    YoutubeDL.getInstance().execute(request, processId) { percentage, _, _ ->
                        progress = percentage.coerceIn(0f, 100f)
                        notificationManager().notify(
                            notificationId,
                            notification(
                                title,
                                progress.toInt(),
                                if (progress >= 99.9f) "Finalizando y uniendo pistas con FFmpeg…" else "Descargando · FFmpeg unirá pistas separadas si hace falta",
                                true,
                            ),
                        )
                    }
                }
                speedMonitor.cancel()
                val saved = outputDir.listFiles().orEmpty()
                    .filter { it.isFile && it.name !in before && !it.name.endsWith(".part") && it.lastModified() >= startedAt }
                    .mapNotNull(::publishToDownloads)
                val completed = saved.any { it }
                val completion = if (completed) "Guardado en Descargas/Linka" else "No se pudo guardar en Descargas; revisa la app"
                feedback.value = DownloadFeedback(completion, isError = !completed)
                notificationManager().notify(notificationId, notification(title, 100, completion, false))
                stopForeground(STOP_FOREGROUND_DETACH)
            } catch (error: Exception) {
                speedMonitor.cancel()
                val detail = usefulError(error)
                feedback.value = DownloadFeedback(detail, isError = true)
                notificationManager().notify(notificationId, notification(title, progress.toInt(), detail, false))
                stopForeground(STOP_FOREGROUND_DETACH)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            )
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun notification(title: String, progress: Int, text: String, ongoing: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_linka)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            notificationManager().createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Descargas de Linka", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun publishToDownloads(source: File): Boolean {
        var destination: android.net.Uri? = null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(source.extension.lowercase()) ?: "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Linka")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            destination = uri
            contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: throw IllegalStateException("No se pudo abrir Descargas para guardar el archivo")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            source.delete()
            true
        } catch (_: Exception) {
            destination?.let { runCatching { contentResolver.delete(it, null, null) } }
            false
        }
    }

    private fun notificationManager() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun usefulError(error: Exception): String {
        val lines = error.message.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val detail = lines.firstOrNull { it.startsWith("ERROR:", ignoreCase = true) }
            ?: lines.firstOrNull { it.contains("unsupported url", ignoreCase = true) || it.contains("HTTP Error", ignoreCase = true) }
        return (detail ?: "No se pudo descargar. El sitio pudo bloquear la solicitud o no ser compatible.")
            .take(220)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        val feedback = MutableStateFlow<DownloadFeedback?>(null)
        private const val CHANNEL_ID = "linka_downloads"
        private const val ACTION_DOWNLOAD = "com.rikiluciano.linka.DOWNLOAD"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_FORMAT_ID = "format_id"
        private const val EXTRA_SELECTOR = "selector"
        private const val EXTRA_AUDIO_ONLY = "audio_only"
        private const val EXTRA_HAS_AUDIO = "has_audio"

        fun start(context: Context, url: String, title: String, quality: DownloadQuality) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                when (quality) {
                    DownloadQuality.Best -> putExtra(EXTRA_SELECTOR, quality.selector())
                    DownloadQuality.AudioMp3 -> putExtra(EXTRA_AUDIO_ONLY, true)
                    is DownloadQuality.Format -> {
                        putExtra(EXTRA_FORMAT_ID, quality.format.id)
                        putExtra(EXTRA_SELECTOR, quality.selector())
                        putExtra(EXTRA_HAS_AUDIO, quality.format.hasAudio)
                    }
                }
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
