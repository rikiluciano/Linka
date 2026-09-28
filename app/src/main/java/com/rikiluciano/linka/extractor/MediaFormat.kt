package com.rikiluciano.linka.extractor

data class MediaFormat(
    val id: String,
    val label: String,
    val height: Int,
    val extension: String,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val bitrate: Double,
) {
    val needsMerge: Boolean get() = hasVideo && !hasAudio
}

data class ExtractedMedia(val title: String, val formats: List<MediaFormat>)

sealed interface DownloadQuality {
    data object Best : DownloadQuality
    data object AudioMp3 : DownloadQuality
    data class Format(val format: MediaFormat) : DownloadQuality

    fun selector(): String = when (this) {
        Best -> "bestvideo*+bestaudio/best"
        AudioMp3 -> "bestaudio/best"
        is Format -> if (format.needsMerge) "${format.id}+bestaudio/best" else format.id
    }
}
