package com.grabix.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grabix.app.GrabixApp
import com.grabix.app.data.AppSettings
import com.grabix.app.download.DownloadRepository
import com.grabix.app.engine.AudioOption
import com.grabix.app.engine.Engine
import com.grabix.app.engine.Entry
import com.grabix.app.engine.MediaInfo
import com.grabix.app.engine.VideoOption
import com.grabix.app.util.Cookies
import com.grabix.app.util.Links
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalyzeState {
    data object Idle : AnalyzeState
    data class Loading(val url: String) : AnalyzeState
    data class Loaded(val info: MediaInfo, val cookieFile: String?) : AnalyzeState
    data class Error(val url: String, val message: String) : AnalyzeState
}

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Entry> = emptyList(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val grabix = app as GrabixApp

    val settings: StateFlow<AppSettings> =
        grabix.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _analyze = MutableStateFlow<AnalyzeState>(AnalyzeState.Idle)
    val analyze: StateFlow<AnalyzeState> = _analyze

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Asks the shell to switch tabs (e.g. open the browser on a site). */
    private val _navigate = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val navigate: SharedFlow<String> = _navigate

    private val _browserUrl = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val browserUrl: SharedFlow<String> = _browserUrl

    private var analyzeJob: Job? = null
    private var searchJob: Job? = null

    fun toast(msg: String) { _messages.tryEmit(msg) }

    /** Fetch qualities for a link and show the download sheet. */
    fun analyze(rawUrl: String) {
        val url = Links.extract(rawUrl) ?: Links.normalize(rawUrl)
        analyzeJob?.cancel()
        _analyze.value = AnalyzeState.Loading(url)
        analyzeJob = viewModelScope.launch {
            val cookies = Cookies.exportFor(grabix, url)
            runCatching { Engine.fetchInfo(grabix, url, cookies) }
                .onSuccess { _analyze.value = AnalyzeState.Loaded(it, cookies) }
                .onFailure { _analyze.value = AnalyzeState.Error(url, friendlyError(it.message)) }
        }
    }

    fun dismissSheet() {
        analyzeJob?.cancel()
        _analyze.value = AnalyzeState.Idle
    }

    fun downloadVideo(info: MediaInfo, option: VideoOption, subs: Boolean, cookieFile: String?, folder: String? = null) {
        viewModelScope.launch {
            val n = DownloadRepository.enqueueVideo(grabix, info, option, subs, cookieFile, folder)
            _analyze.value = AnalyzeState.Idle
            toast(if (n == 1) "Download started" else if (folder.isNullOrBlank()) "$n downloads added" else "$n downloads added to “$folder”")
        }
    }

    fun downloadAudio(info: MediaInfo, option: AudioOption, cookieFile: String?, folder: String? = null) {
        viewModelScope.launch {
            val n = DownloadRepository.enqueueAudio(grabix, info, option, cookieFile, folder)
            _analyze.value = AnalyzeState.Idle
            toast(if (n == 1) "Download started" else if (folder.isNullOrBlank()) "$n downloads added" else "$n downloads added to “$folder”")
        }
    }

    /** Home box: URL → analyze, words → YouTube search. */
    fun submit(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (Links.looksLikeUrl(t) || Links.extract(t) != null) analyze(t) else runSearch(t)
    }

    fun runSearch(query: String) {
        searchJob?.cancel()
        _search.value = SearchState(query = query, loading = true)
        searchJob = viewModelScope.launch {
            grabix.settings.addSearch(query)
            runCatching { Engine.search(grabix, query) }
                .onSuccess { _search.value = SearchState(query, false, it) }
                .onFailure { _search.value = SearchState(query, false, emptyList(), friendlyError(it.message)) }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState()
    }

    fun clearHistory() = viewModelScope.launch { grabix.settings.clearSearchHistory() }

    fun openInBrowser(url: String) {
        _browserUrl.tryEmit(url)
        _navigate.tryEmit("browser")
    }

    fun goTo(route: String) { _navigate.tryEmit(route) }

    private fun friendlyError(raw: String?): String {
        val m = raw ?: return "Could not read this link"
        return when {
            m.contains("Unsupported URL", true) -> "This page has no downloadable video. Open the video itself and try again."
            m.contains("login", true) || m.contains("cookies", true) || m.contains("sign in", true) ->
                "This site needs you to sign in. Log in inside the Grabix browser, then tap Download again."
            m.contains("Private", true) -> "This video is private."
            m.contains("Unable to download webpage", true) || m.contains("timed out", true) ->
                "Network problem. Check your connection and try again."
            m.contains("confirm your age", true) -> "Age-restricted. Sign in inside the Grabix browser, then retry."
            else -> m.lineSequence().lastOrNull { it.contains("ERROR") }?.substringAfter("ERROR:")?.trim()?.take(220)
                ?: m.take(220)
        }
    }
}
