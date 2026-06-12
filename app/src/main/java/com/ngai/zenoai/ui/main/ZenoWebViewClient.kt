package com.ngai.zenoai.ui.main

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewClientCompat
import com.ngai.zenoai.utils.Constants
import com.ngai.zenoai.utils.NetworkUtils
import com.ngai.zenoai.viewmodel.MainViewModel
import com.ngai.zenoai.viewmodel.WebViewError

class ZenoWebViewClient(
    private val viewModel: MainViewModel,
    private val onExternalLink: (String) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        viewModel.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        viewModel.onPageFinished(
            url = url,
            title = view.title ?: "",
            canGoBack = view.canGoBack()
        )
        injectJavaScriptBridge(view)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val url = request.url.toString()

        // Block non-HTTPS and malicious schemes
        if (NetworkUtils.isMaliciousScheme(url)) {
            return true
        }

        // Let internal app URLs load normally
        if (isInternalUrl(url)) {
            return false
        }

        // Open external links in browser
        if (request.isForMainFrame) {
            onExternalLink(url)
            return true
        }

        return false
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (!request.isForMainFrame) return

        val errorType = when (error.errorCode) {
            ERROR_HOST_LOOKUP,
            ERROR_CONNECT,
            ERROR_TIMEOUT -> WebViewError.NO_INTERNET
            ERROR_FAILED_SSL_HANDSHAKE -> WebViewError.SSL_ERROR
            ERROR_FILE_NOT_FOUND -> WebViewError.PAGE_NOT_FOUND
            ERROR_BAD_URL -> WebViewError.GENERAL_ERROR
            else -> WebViewError.SERVER_ERROR
        }

        viewModel.onErrorReceived(errorType)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (!request.isForMainFrame) return

        val error = when (errorResponse.statusCode) {
            404 -> WebViewError.PAGE_NOT_FOUND
            in 500..599 -> WebViewError.SERVER_ERROR
            else -> WebViewError.GENERAL_ERROR
        }
        viewModel.onErrorReceived(error)
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        // Cancel SSL errors — do NOT proceed with invalid certificates
        handler.cancel()
        viewModel.onErrorReceived(WebViewError.SSL_ERROR)
    }

    private fun isInternalUrl(url: String): Boolean {
        val internals = listOf(
            "zenoai-bot.vercel.app",
            "accounts.google.com",
            "firebase.google.com",
            "firebaseapp.com",
            "googleapis.com",
            "about:blank"
        )
        return internals.any { url.contains(it) } || url.startsWith("blob:") ||
                url.startsWith("data:")
    }

    /**
     * Inject a JavaScript bridge so the web app can call native Android APIs.
     */
    private fun injectJavaScriptBridge(view: WebView) {
        view.evaluateJavascript(
            """
            (function() {
                if (!window._zenoNativeBridgeInjected) {
                    window._zenoNativeBridgeInjected = true;
                    
                    // Override share functionality
                    if (!window.zenoShare) {
                        window.zenoShare = function(text, title) {
                            if (window.${Constants.JS_INTERFACE_NAME}) {
                                window.${Constants.JS_INTERFACE_NAME}.shareContent(text, title || '');
                            }
                        };
                    }
                    
                    // Haptic feedback
                    if (!window.zenoHaptic) {
                        window.zenoHaptic = function(type) {
                            if (window.${Constants.JS_INTERFACE_NAME}) {
                                window.${Constants.JS_INTERFACE_NAME}.hapticFeedback(type || 'light');
                            }
                        };
                    }
                }
            })();
            """.trimIndent(),
            null
        )
    }
}
