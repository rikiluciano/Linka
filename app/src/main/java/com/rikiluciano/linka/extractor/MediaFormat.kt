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

data class ExtractedMedia(val title: String, val formats: List<MediaFormat>) {
    /** One useful format per source resolution; never fabricates a resolution the source lacks. */
    fun videoQualities(): List<MediaFormat> = formats.asSequence()
        .filter { it.hasVideo && it.height > 0 }
        .groupBy(MediaFormat::height)
        .values
        .mapNotNull { variants ->
            variants.maxWithOrNull(compareBy<MediaFormat> { if (it.hasAudio) 0 else 1 }.thenBy { it.bitrate })
        }
        .sortedByDescending(MediaFormat::height)

    fun hasAudioSource(): Boolean = formats.any(MediaFormat::hasAudio)
}

sealed interface DownloadQuality {
    data object Best : DownloadQuality
    data object AudioMp3 : DownloadQuality
    data class Format(val format: MediaFormat) : DownloadQuality

    fun selector(): String = when (this) {
        Best -> "bestvideo*+bestaudio/best"
        AudioMp3 -> "bestaudio/best"
        is Format -> if (format.needsMerge) {
            "${format.id}+bestaudio/best[height<=${format.height}]"
        } else format.id
    }
}
