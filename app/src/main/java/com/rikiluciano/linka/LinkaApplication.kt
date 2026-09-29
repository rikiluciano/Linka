package com.rikiluciano.linka

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LinkaApplication : Application() {
    private val updateMutex = Mutex()
    @Volatile private var ytDlpUpdatedThisSession = false

    @Volatile var extractorReady: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)
            extractorReady = true
        } catch (error: Exception) {
            Log.e("Linka", "No se pudo inicializar el motor multimedia", error)
        }
    }

    /** Refresh the bundled extractor once per process before inspecting or downloading a URL. */
    suspend fun ensureYtDlpUpdated() = withContext(Dispatchers.IO) {
        check(extractorReady) { "El motor multimedia no pudo inicializarse." }
        updateMutex.withLock {
            if (!ytDlpUpdatedThisSession) {
                YoutubeDL.getInstance().updateYoutubeDL(this@LinkaApplication, YoutubeDL.UpdateChannel.STABLE)
                ytDlpUpdatedThisSession = true
            }
        }
    }
}
