package com.rikiluciano.linka.update

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val notes: String,
)

sealed interface AppUpdateState {
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data object Dismissed : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Downloading(val release: AppRelease, val progress: Int) : AppUpdateState
    data class ReadyToInstall(val release: AppRelease, val apkPath: String) : AppUpdateState
    data class Installing(val version: String) : AppUpdateState
    data class Failed(val version: String?, val message: String) : AppUpdateState
}

object AppUpdateEvents {
    private val mutableState = MutableStateFlow<AppUpdateState>(AppUpdateState.Checking)
    val state = mutableState.asStateFlow()
    fun set(value: AppUpdateState) { mutableState.value = value }
}

object AppUpdateChecker {
    private const val RELEASE_API = "https://api.github.com/repos/rikiluciano/Linka/releases/latest"

    fun check(context: Context): AppRelease? {
        val installedVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Linka-Android-App")
        return try {
            if (connection.responseCode !in 200..299) error("GitHub respondió ${connection.responseCode}")
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
            val version = release.optString("tag_name").removePrefix("v")
            if (compareVersions(version, installedVersion) <= 0) return null
            val assets = release.optJSONArray("assets") ?: error("El release no contiene archivos")
            val apk = (0 until assets.length()).asSequence().map(assets::getJSONObject)
                .firstOrNull { it.optString("name") == "linka.apk" } ?: error("El release no contiene linka.apk")
            val downloadUrl = apk.getString("browser_download_url")
            val downloadUri = android.net.Uri.parse(downloadUrl)
            if (downloadUri.scheme != "https" || downloadUri.host != "github.com" ||
                !downloadUri.path.orEmpty().contains("/releases/download/")
            ) error("El enlace de descarga del release no es de GitHub.")
            val digest = apk.optString("digest").removePrefix("sha256:")
            if (!digest.matches(Regex("[a-fA-F0-9]{64}"))) error("El release no publica un SHA-256 válido")
            AppRelease(
                version = version,
                downloadUrl = downloadUrl,
                sha256 = digest.lowercase(),
                sizeBytes = apk.optLong("size"),
                notes = release.optString("body").take(1200),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val lhs = left.split('.').map { it.toIntOrNull() ?: 0 }
        val rhs = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(lhs.size, rhs.size)) {
            val comparison = lhs.getOrElse(index) { 0 }.compareTo(rhs.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
