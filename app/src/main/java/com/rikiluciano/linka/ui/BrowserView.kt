package com.rikiluciano.linka.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.SafeBrowsingResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserView(
    url: String?,
    modifier: Modifier = Modifier,
    onPageChanged: (String?) -> Unit,
    onVideoDetected: (String?) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.safeBrowsingEnabled = true
                webViewClient = object : WebViewClient() {
                    private val mainHandler = Handler(Looper.getMainLooper())
                    private var lastDetection = ""

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val scheme = request.url.scheme
                        if (scheme != "https" && scheme != "http") return true
                        return false
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                        super.shouldInterceptRequest(view, request).also {
                            if (!request.isForMainFrame && looksLikeMedia(request.url)) {
                                mainHandler.post {
                                    val page = view.url ?: request.url.toString()
                                    if (lastDetection != page) {
                                        lastDetection = page
                                        onVideoDetected(page)
                                    }
                                }
                            }
                        }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        lastDetection = ""
                        onPageChanged(url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        onPageChanged(url)
                        // Read-only DOM inspection. No JavascriptInterface is exposed to untrusted pages.
                        view.evaluateJavascript(
                            "(function(){return Array.from(document.querySelectorAll('video,video source')).some(function(v){return !!(v.currentSrc||v.src);});})()",
                        ) { result ->
                            if (result == "true") onVideoDetected(url)
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (!url.isNullOrBlank() && webView.url != url) webView.loadUrl(url)
        },
    )
}

private fun looksLikeMedia(uri: Uri): Boolean {
    val path = uri.path?.lowercase().orEmpty()
    return listOf(".mp4", ".m4v", ".webm", ".mov", ".m3u8", ".mpd", ".mp3", ".m4a", ".aac", ".aac").any(path::contains)
}
