package com.rikiluciano.linka.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

private const val INSTALL_STATUS_ACTION = "com.rikiluciano.linka.INSTALL_STATUS"

object AppInstaller {
    fun install(context: Context, apkFile: File, version: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        params.setSize(apkFile.length())
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input -> session.openWrite("linka-update.apk", 0, apkFile.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            } }
            AppUpdateEvents.set(AppUpdateState.Installing(version))
            val callback = Intent(context, AppInstallReceiver::class.java).apply {
                action = INSTALL_STATUS_ACTION
                putExtra(EXTRA_VERSION, version)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pending.intentSender)
        }
    }
}

class AppInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val version = intent.getStringExtra("version").orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmation == null) {
                    AppUpdateEvents.set(AppUpdateState.Failed(version, "Android no devolvió la confirmación de instalación."))
                } else {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmation)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppUpdateEvents.set(AppUpdateState.UpToDate)
                File(context.cacheDir, "app-updates/linka-$version.apk").delete()
            }
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                AppUpdateEvents.set(AppUpdateState.Failed(version, "Android no pudo instalar la actualización${detail?.let { ": $it" }.orEmpty()}"))
            }
        }
    }
}

internal fun android.content.pm.PackageInfo.signingCertificates(): List<String> {
    val certificateStrings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners?.map { it.toCharsString() }
    } else {
        @Suppress("DEPRECATION") this.signatures?.map { it.toCharsString() }
    }
    return certificateStrings.orEmpty()
}
