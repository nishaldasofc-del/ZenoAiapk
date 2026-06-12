package com.ngai.zenoai.viewmodel

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ngai.zenoai.utils.Constants
import com.ngai.zenoai.utils.NetworkLiveData
import com.ngai.zenoai.utils.PreferenceManager

enum class WebViewError {
    NO_INTERNET,
    SERVER_ERROR,
    SSL_ERROR,
    PAGE_NOT_FOUND,
    GENERAL_ERROR
}

data class PageState(
    val url: String = Constants.BASE_URL,
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val error: WebViewError? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val networkLiveData = NetworkLiveData(application)
    private val prefManager = PreferenceManager(application)

    private val _pageState = MutableLiveData(PageState())
    val pageState: LiveData<PageState> = _pageState

    private val _showExitDialog = MutableLiveData(false)
    val showExitDialog: LiveData<Boolean> = _showExitDialog

    private val _snackbarMessage = MutableLiveData<String?>()
    val snackbarMessage: LiveData<String?> = _snackbarMessage

    fun getStartUrl(): String {
        return prefManager.lastVisitedUrl
    }

    fun onPageStarted(url: String) {
        _pageState.value = _pageState.value?.copy(
            url = url,
            isLoading = true,
            error = null,
            progress = 0
        )
    }

    fun onPageFinished(url: String, title: String, canGoBack: Boolean) {
        prefManager.lastVisitedUrl = url
        _pageState.value = _pageState.value?.copy(
            url = url,
            title = title,
            isLoading = false,
            progress = 100,
            canGoBack = canGoBack,
            error = null
        )
    }

    fun onProgressChanged(progress: Int) {
        _pageState.value = _pageState.value?.copy(progress = progress)
    }

    fun onErrorReceived(error: WebViewError) {
        _pageState.value = _pageState.value?.copy(
            isLoading = false,
            error = error
        )
    }

    fun onBackPressed(webView: WebView?): Boolean {
        return if (webView?.canGoBack() == true) {
            webView.goBack()
            true
        } else {
            _showExitDialog.value = true
            false
        }
    }

    fun dismissExitDialog() {
        _showExitDialog.value = false
    }

    fun onNetworkRestored() {
        _snackbarMessage.value = "Connected"
    }

    fun onNetworkLost() {
        _snackbarMessage.value = "No internet connection"
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    fun isHomeUrl(url: String): Boolean {
        return url.trimEnd('/') == Constants.BASE_URL.trimEnd('/')
    }
}
