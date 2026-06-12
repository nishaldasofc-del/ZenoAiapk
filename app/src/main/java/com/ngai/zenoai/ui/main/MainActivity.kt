package com.ngai.zenoai.ui.main

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import com.ngai.zenoai.BuildConfig
import com.ngai.zenoai.R
import com.ngai.zenoai.databinding.ActivityMainBinding
import com.ngai.zenoai.utils.Constants
import com.ngai.zenoai.utils.HapticUtils
import com.ngai.zenoai.utils.NetworkUtils
import com.ngai.zenoai.utils.PreferenceManager
import com.ngai.zenoai.utils.hide
import com.ngai.zenoai.utils.openInBrowser
import com.ngai.zenoai.utils.shareText
import com.ngai.zenoai.utils.show
import com.ngai.zenoai.viewmodel.MainViewModel
import com.ngai.zenoai.viewmodel.WebViewError
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var prefManager: PreferenceManager

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null
    private var webViewSavedState: Bundle? = null

    // ── Activity result launchers ──────────────────────────────────────────────

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            filePathCallback?.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
            filePathCallback = null
        }

    private val cameraCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && capturedImageUri != null) {
                filePathCallback?.onReceiveValue(arrayOf(capturedImageUri!!))
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
            capturedImageUri = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            handlePermissionResults(results)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initFcm()
            }
        }

    private var capturedImageUri: Uri? = null

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefManager = PreferenceManager(this)

        setupWebView()
        setupObservers()
        setupSwipeRefresh()
        setupErrorScreens()
        setupBackNavigation()
        requestNotificationPermission()

        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            loadInitialUrl(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadInitialUrl(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        binding.webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        binding.webView.pauseTimers()
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            clearHistory()
            destroy()
        }
        super.onDestroy()
    }

    // ── WebView Setup ──────────────────────────────────────────────────────────

    private fun setupWebView() {
        val webView = binding.webView

        // Disable debugging in release
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true

            // DOM & Storage
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false // security: no local file access
            allowContentAccess = false

            // Display
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false

            // Caching
            cacheMode = WebSettings.LOAD_DEFAULT

            // Media
            mediaPlaybackRequiresUserGesture = false

            // Mixed content — block HTTP on HTTPS pages
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // User agent
            userAgentString = "${userAgentString} ZenoAiApp/1.0 Android"
        }

        // Hardware acceleration is set in manifest/window
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

        // Cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // WebViewClient
        webView.webViewClient = ZenoWebViewClient(
            viewModel = viewModel,
            onExternalLink = { url -> openInBrowser(url) }
        )

        // WebChromeClient
        webView.webChromeClient = ZenoWebChromeClient(
            activity = this,
            onFileChooserRequest = { callback, params -> handleFileChooser(callback, params) },
            onPermissionRequest = { request -> handleWebPermission(request) },
            onProgressChanged = { progress -> viewModel.onProgressChanged(progress) }
        )

        // JavaScript Interface
        webView.addJavascriptInterface(
            ZenoJsInterface(
                context = this,
                onShare = { text, title -> shareText(text, title) },
                onGetFcmToken = { prefManager.fcmToken }
            ),
            Constants.JS_INTERFACE_NAME
        )

        // Download listener
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            downloadFile(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun loadInitialUrl(intent: Intent?) {
        val deepLinkUrl = intent?.data?.toString()
        val startUrl = when {
            deepLinkUrl != null && deepLinkUrl.startsWith("https://zenoai-bot.vercel.app") ->
                deepLinkUrl
            intent?.getStringExtra(Constants.EXTRA_URL) != null ->
                intent.getStringExtra(Constants.EXTRA_URL)!!
            else -> viewModel.getStartUrl()
        }
        binding.webView.loadUrl(startUrl)
    }

    // ── Observers ──────────────────────────────────────────────────────────────

    private fun setupObservers() {
        // Page state
        viewModel.pageState.observe(this) { state ->
            updateProgressBar(state.progress, state.isLoading)

            when {
                state.error != null -> showErrorScreen(state.error)
                state.isLoading -> hideErrorScreens()
                else -> hideErrorScreens()
            }
        }

        // Network
        viewModel.networkLiveData.observe(this) { isConnected ->
            onNetworkChanged(isConnected)
        }

        // Exit dialog
        viewModel.showExitDialog.observe(this) { show ->
            if (show) showExitDialog()
        }

        // Snackbar
        viewModel.snackbarMessage.observe(this) { message ->
            if (message != null) {
                showSnackbar(message)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    // ── Network Handling ───────────────────────────────────────────────────────

    private var wasOffline = false

    private fun onNetworkChanged(isConnected: Boolean) {
        if (!isConnected) {
            wasOffline = true
            viewModel.onNetworkLost()
            if (viewModel.pageState.value?.error == null) {
                viewModel.onErrorReceived(WebViewError.NO_INTERNET)
            }
        } else if (wasOffline) {
            wasOffline = false
            viewModel.onNetworkRestored()
            hideErrorScreens()
            binding.webView.reload()
        }
    }

    // ── Pull to Refresh ────────────────────────────────────────────────────────

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.apply {
            setColorSchemeResources(R.color.primary, R.color.secondary)
            setProgressBackgroundColorSchemeResource(R.color.surface)
            setOnRefreshListener {
                HapticUtils.vibrateLight(this@MainActivity)
                binding.webView.reload()
                postDelayed({ isRefreshing = false }, 1000)
            }
        }
    }

    // ── Error Screens ──────────────────────────────────────────────────────────

    private fun setupErrorScreens() {
        binding.errorNoInternet.btnRetry.setOnClickListener {
            HapticUtils.vibrateLight(this)
            if (NetworkUtils.isConnected(this)) {
                hideErrorScreens()
                binding.webView.reload()
            } else {
                showSnackbar(getString(R.string.error_still_offline))
            }
        }

        binding.errorNoInternet.btnLoadCached.setOnClickListener {
            HapticUtils.vibrateLight(this)
            binding.webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            hideErrorScreens()
            binding.webView.reload()
        }

        binding.errorServer.btnRetry.setOnClickListener {
            HapticUtils.vibrateLight(this)
            hideErrorScreens()
            binding.webView.reload()
        }

        binding.errorSsl.btnRetry.setOnClickListener {
            HapticUtils.vibrateLight(this)
            hideErrorScreens()
            binding.webView.loadUrl(Constants.BASE_URL)
        }

        binding.errorGeneral.btnRetry.setOnClickListener {
            HapticUtils.vibrateLight(this)
            hideErrorScreens()
            binding.webView.reload()
        }
    }

    private fun showErrorScreen(error: WebViewError) {
        hideErrorScreens()
        binding.swipeRefreshLayout.isRefreshing = false

        val errorView = when (error) {
            WebViewError.NO_INTERNET -> binding.errorNoInternet.root
            WebViewError.SERVER_ERROR -> binding.errorServer.root
            WebViewError.SSL_ERROR -> binding.errorSsl.root
            WebViewError.PAGE_NOT_FOUND -> binding.errorGeneral.root.also {
                binding.errorGeneral.tvErrorTitle.text = getString(R.string.error_not_found_title)
                binding.errorGeneral.tvErrorMessage.text =
                    getString(R.string.error_not_found_message)
            }
            WebViewError.GENERAL_ERROR -> binding.errorGeneral.root
        }
        errorView.show()
        HapticUtils.vibrateMedium(this)
    }

    private fun hideErrorScreens() {
        binding.errorNoInternet.root.hide()
        binding.errorServer.root.hide()
        binding.errorSsl.root.hide()
        binding.errorGeneral.root.hide()
    }

    // ── Progress Bar ───────────────────────────────────────────────────────────

    private fun updateProgressBar(progress: Int, isLoading: Boolean) {
        if (isLoading && progress < 100) {
            binding.progressBar.show()
            binding.progressBar.progress = progress
        } else {
            binding.progressBar.hide()
        }
    }

    // ── Back Navigation ────────────────────────────────────────────────────────

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                viewModel.onBackPressed(binding.webView)
            }
        })
    }

    private fun showExitDialog() {
        HapticUtils.vibrateLight(this)
        AlertDialog.Builder(this, R.style.AlertDialogStyle)
            .setTitle(R.string.exit_dialog_title)
            .setMessage(R.string.exit_dialog_message)
            .setPositiveButton(R.string.exit) { _, _ ->
                HapticUtils.vibrateMedium(this)
                finish()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                viewModel.dismissExitDialog()
            }
            .setOnCancelListener {
                viewModel.dismissExitDialog()
            }
            .show()
    }

    // ── File Chooser ───────────────────────────────────────────────────────────

    private fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ) {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        val acceptTypes = params.acceptTypes.joinToString(",")
        val isImageOnly = acceptTypes.contains("image") && !acceptTypes.contains("video")
        val isCameraCapture = params.isCaptureEnabled

        if (isCameraCapture && isImageOnly) {
            checkCameraPermissionAndCapture()
        } else {
            val mimeType = when {
                acceptTypes.contains("image") && acceptTypes.contains("video") -> "*/*"
                acceptTypes.contains("image") -> "image/*"
                acceptTypes.contains("video") -> "video/*"
                acceptTypes.contains("audio") -> "audio/*"
                acceptTypes.contains("pdf") -> "application/pdf"
                else -> "*/*"
            }
            fileChooserLauncher.launch(mimeType)
        }
    }

    private fun checkCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun launchCamera() {
        try {
            val imageFile = java.io.File(
                getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "zeno_capture_${System.currentTimeMillis()}.jpg"
            )
            capturedImageUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                imageFile
            )
            cameraCaptureLauncher.launch(capturedImageUri!!)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    // ── WebView Permissions ───────────────────────────────────────────────────

    private fun handleWebPermission(request: PermissionRequest) {
        pendingPermissionRequest = request
        val androidPermissions = mutableListOf<String>()

        for (resource in request.resources) {
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    androidPermissions.add(Manifest.permission.CAMERA)
                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    androidPermissions.add(Manifest.permission.RECORD_AUDIO)
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> { /* no Android mapping */ }
            }
        }

        if (androidPermissions.isEmpty()) {
            request.grant(request.resources)
            return
        }

        val allGranted = androidPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            request.grant(request.resources)
        } else {
            permissionLauncher.launch(androidPermissions.toTypedArray())
        }
    }

    private fun handlePermissionResults(results: Map<String, Boolean>) {
        val request = pendingPermissionRequest ?: return
        val allGranted = results.values.all { it }

        if (allGranted) {
            request.grant(request.resources)
        } else {
            request.deny()
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
        }
        pendingPermissionRequest = null
    }

    // ── Downloads ──────────────────────────────────────────────────────────────

    private fun downloadFile(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val cookies = CookieManager.getInstance().getCookie(url)

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setTitle(fileName)
                setDescription(getString(R.string.downloading))
                addRequestHeader("cookie", cookies)
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            HapticUtils.vibrateLight(this)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Firebase / Notifications ───────────────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    initFcm()
                }
                ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) -> {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.notification_permission_title)
                        .setMessage(R.string.notification_permission_message)
                        .setPositiveButton(R.string.allow) { _, _ ->
                            notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                        .setNegativeButton(R.string.later, null)
                        .show()
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            initFcm()
        }
    }

    private fun initFcm() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                prefManager.fcmToken = token
                if (BuildConfig.DEBUG) {
                    android.util.Log.d("ZenoAi/FCM", "Token: $token")
                }
            }
        }
    }

    // ── Snackbar ───────────────────────────────────────────────────────────────

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).apply {
            setBackgroundTint(ContextCompat.getColor(this@MainActivity, R.color.surface_variant))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.on_surface))
        }.show()
    }
}
