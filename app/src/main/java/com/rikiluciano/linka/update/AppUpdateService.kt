package com.rikiluciano.linka.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rikiluciano.linka.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Actualizaciones de Linka", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_DOWNLOAD) { stopSelf(startId); return START_NOT_STICKY }
        val release = AppRelease(
            intent.getStringExtra(EXTRA_VERSION).orEmpty(),
            intent.getStringExtra(EXTRA_URL).orEmpty(),
            intent.getStringExtra(EXTRA_SHA256).orEmpty(),
            intent.getLongExtra(EXTRA_SIZE, 0L),
            intent.getStringExtra(EXTRA_NOTES).orEmpty(),
        )
        if (release.version.isBlank() || release.downloadUrl.isBlank() || release.sha256.isBlank()) {
            AppUpdateEvents.set(AppUpdateState.Failed(null, "Los datos del release están incompletos."))
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startInForeground(notification("Descargando Linka ${release.version}", 0, true))
        scope.launch {
            try {
                val file = File(File(cacheDir, "app-updates").apply { mkdirs() }, "linka-${release.version}.apk")
                val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Linka-Android-App")
                try {
                    if (connection.responseCode !in 200..299) error("No se pudo descargar el APK (HTTP ${connection.responseCode}).")
                    val total = connection.contentLengthLong
                    var received = 0L
                    val temporary = File(file.parentFile, "${file.name}.tmp")
                    connection.inputStream.use { input -> temporary.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            val progress = if (total > 0) ((received * 100) / total).toInt().coerceIn(0, 99) else 0
                            AppUpdateEvents.set(AppUpdateState.Downloading(release, progress))
                            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                .notify(NOTIFICATION_ID, notification("Descargando Linka ${release.version}", progress, true))
                        }
                    } }
                    if (sha256(temporary) != release.sha256) error("La verificación de seguridad del APK no coincidió. No se instalará.")
                    val packageInfo = packageManager.getPackageArchiveInfo(temporary.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                        ?: error("El archivo descargado no es un APK válido.")
                    if (packageInfo.packageName != packageName) error("El APK no pertenece a Linka.")
                    val installedInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val installedSignatures = installedInfo.signingCertificates().toSet()
                    val downloadedSignatures = packageInfo.signingCertificates().toSet()
                    if (installedSignatures.isEmpty() || downloadedSignatures.isEmpty() || installedSignatures != downloadedSignatures) {
                        error("La firma no coincide con la versión instalada. Hay que instalar una vez la versión firmada con la clave estable de Linka.")
                    }
                    @Suppress("DEPRECATION")
                    val newer = packageInfo.versionCode > installedInfo.versionCode
                    if (!newer) error("El APK descargado no es más reciente que esta versión.")
                    if (file.exists()) file.delete()
                    if (!temporary.renameTo(file)) error("No se pudo guardar el APK en el espacio privado de Linka.")
                    AppUpdateEvents.set(AppUpdateState.ReadyToInstall(release, file.absolutePath))
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                        .notify(NOTIFICATION_ID, notification("Linka ${release.version} está lista para instalar", 100, false))
                    stopForeground(STOP_FOREGROUND_DETACH)
                } finally { connection.disconnect() }
            } catch (error: Exception) {
                File(cacheDir, "app-updates").listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
                val message = error.message ?: "No se pudo descargar la actualización."
                AppUpdateEvents.set(AppUpdateState.Failed(release.version, message))
                (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(NOTIFICATION_ID, notification(message.take(90), 0, false))
                stopForeground(STOP_FOREGROUND_DETACH)
            } finally { stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun startInForeground(value: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, value, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, value)
    }

    private fun notification(text: String, progress: Int, ongoing: Boolean) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_linka).setContentTitle("Actualización de Linka").setContentText(text)
        .setOnlyAlertOnce(true).setOngoing(ongoing).setProgress(100, progress.coerceIn(0, 100), progress == 0)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS).build()

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "linka_updates"
        private const val NOTIFICATION_ID = 4202
        private const val ACTION_DOWNLOAD = "com.rikiluciano.linka.UPDATE_DOWNLOAD"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_URL = "url"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_SIZE = "size"
        private const val EXTRA_NOTES = "notes"
        fun start(context: Context, release: AppRelease) {
            val intent = Intent(context, AppUpdateService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_VERSION, release.version); putExtra(EXTRA_URL, release.downloadUrl)
                putExtra(EXTRA_SHA256, release.sha256); putExtra(EXTRA_NOTES, release.notes)
                putExtra(EXTRA_SIZE, release.sizeBytes)
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
