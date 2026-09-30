package com.rikiluciano.linka.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.util.Locale

/** Queues an explicit HTTP(S) resource download through Android's system download manager. */
object DirectFileDownload {
    fun enqueue(context: Context, rawUrl: String): String {
        val uri = Uri.parse(rawUrl.trim())
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "El enlace debe ser una dirección HTTP o HTTPS válida."
        }

        val name = safeFileName(uri.lastPathSegment)
        val request = DownloadManager.Request(uri).apply {
            setTitle(name)
            setDescription("Linka · Descargas")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Linka/$name")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        return name
    }

    private fun safeFileName(raw: String?): String {
        val decoded = raw.orEmpty()
            .replace(Regex("[\\x00-\\x1F\\\\/:*?\"<>|]"), "_")
            .trim().trim('.')
            .take(160)
        val base = decoded.ifBlank { "archivo" }
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val extension = if (dot > 0) base.substring(dot).take(20).lowercase(Locale.ROOT) else ""
        return "${stem.take(110)}-${System.currentTimeMillis()}$extension"
    }
}
