package com.rikiluciano.linka

import android.app.Application
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class LinkaApplication : Application() {
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
}
