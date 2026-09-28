package com.rikiluciano.linka.extractor

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoExtractor {
    suspend fun inspect(pageUrl: String): ExtractedMedia = withContext(Dispatchers.IO) {
        requireHttpUrl(pageUrl)
        val info = YoutubeDL.getInstance().getInfo(pageUrl)
        val formats = info.formats.orEmpty().mapNotNull { item ->
            val id = item.formatId?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val video = !item.vcodec.isNullOrBlank() && item.vcodec != "none"
            val audio = !item.acodec.isNullOrBlank() && item.acodec != "none"
            if (!video && !audio) return@mapNotNull null
            val height = item.height ?: 0
            val ext = item.ext ?: "media"
            val label = when {
                video && audio -> "${height.takeIf { it > 0 }?.let { "${it}p · " } ?: ""}$ext · audio y video"
                video -> "${height.takeIf { it > 0 }?.let { "${it}p · " } ?: "Video · "}$ext · requiere unir audio"
                else -> "Audio · $ext · ${item.formatNote.orEmpty()}"
            }
            MediaFormat(id, label, height, ext, video, audio, (item.tbr ?: 0).toDouble())
        }.distinctBy(MediaFormat::id)
            .sortedWith(compareByDescending<MediaFormat> { it.height }.thenByDescending { it.bitrate })
        ExtractedMedia(info.title?.takeIf(String::isNotBlank) ?: "Video", formats)
    }

    fun buildRequest(pageUrl: String, quality: DownloadQuality, outputTemplate: String): YoutubeDLRequest {
        requireHttpUrl(pageUrl)
        return YoutubeDLRequest(pageUrl).apply {
            addOption("--no-playlist")
            addOption("-o", outputTemplate)
            addOption("-f", quality.selector())
            when (quality) {
                DownloadQuality.AudioMp3 -> {
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0")
                }
                // yt-dlp/FFmpeg pick a compatible container for the selected codecs.
            }
        }
    }

    private fun requireHttpUrl(raw: String) {
        val uri = android.net.Uri.parse(raw)
        require(uri.scheme == "https" || uri.scheme == "http") { "Solo se admiten enlaces HTTP o HTTPS." }
        require(!uri.host.isNullOrBlank()) { "El enlace no contiene un host válido." }
    }
}
