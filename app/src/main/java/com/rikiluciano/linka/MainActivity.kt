package com.rikiluciano.linka

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rikiluciano.linka.ui.LinkaScreen
import com.rikiluciano.linka.ui.LinkaViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: LinkaViewModel = viewModel()
            LinkaScreen(model = model)
        }
        acceptIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIncomingIntent(intent)
    }

    private fun acceptIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val address = shared ?: intent.dataString
        if (!address.isNullOrBlank()) {
            window.decorView.post {
                val model = androidx.lifecycle.ViewModelProvider(this)[LinkaViewModel::class.java]
                model.openSharedLink(address)
            }
        }
    }
}
