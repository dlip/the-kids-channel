package com.thekidschannel

import android.net.Uri
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.thekidschannel.data.RootEntity
import com.thekidschannel.data.ChannelStatsEntity
import com.thekidschannel.data.RootRepository
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.ChannelScanner
import com.thekidschannel.media.VideoItem
import com.thekidschannel.media.neighborChannelUris
import com.thekidschannel.media.relativeChannelIndex
import com.thekidschannel.media.resolveResumePoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class ChannelPlayback(
    val videos: List<VideoItem>,
    val startVideoIndex: Int,
    val startPositionMs: Long,
)

data class MainUiState(
    val roots: List<RootEntity> = emptyList(),
    val channelStats: List<ChannelStatsEntity> = emptyList(),
    val channels: List<ChannelFolder> = emptyList(),
    val selectedChannel: ChannelFolder? = null,
    val videos: List<VideoItem> = emptyList(),
    val preparedChannels: Map<String, ChannelPlayback> = emptyMap(),
    val startVideoIndex: Int = 0,
    val startPositionMs: Long = 0,
    val previewPath: String? = null,
    val previewUpdatedAt: Long = 0,
    val normalizeAudio: Boolean = true,
    val audioLanguage: String = "en",
    val subtitleLanguage: String = "en",
    val subtitlesEnabled: Boolean = true,
    val subtitleFontSize: Int = 100,
    val isLoading: Boolean = true,
    val message: String? = null,
)

class MainViewModel(
    private val repository: RootRepository,
    private val scanner: ChannelScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MainUiState(
            normalizeAudio = repository.normalizeAudio,
            audioLanguage = repository.audioLanguage,
            subtitleLanguage = repository.subtitleLanguage,
            subtitlesEnabled = repository.subtitlesEnabled,
            subtitleFontSize = repository.subtitleFontSize,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState
    private var channelLoadJob: Job? = null
    private val progressSaveMutex = Mutex()
    private val previewSaveMutex = Mutex()

    init {
        viewModelScope.launch {
            repository.roots.collect { roots ->
                _uiState.update { it.copy(roots = roots) }
            }
        }
        viewModelScope.launch {
            repository.channelStats.collect { stats ->
                _uiState.update { it.copy(channelStats = stats) }
            }
        }
        viewModelScope.launch {
            repository.roots
                .map { roots -> roots.map { it.copy(watchTimeMs = 0) } }
                .distinctUntilChanged()
                .collectLatest { roots ->
                    _uiState.update {
                        it.copy(
                            isLoading = roots.any { it.enabled },
                            preparedChannels = emptyMap(),
                            message = null,
                        )
                    }
                    val discoveredChannels = scanner.discoverChannels(roots)
                    repository.rememberChannels(discoveredChannels)
                    val enabledRoots = roots.filter { it.enabled }.map { it.uri }.toSet()
                    val channels = discoveredChannels.filter { it.rootUri in enabledRoots }
                    _uiState.update { it.copy(channels = channels) }
                    val selectedChannel = channels.firstOrNull {
                        it.uri == repository.selectedChannelUri
                    } ?: channels.firstOrNull()
                    selectChannel(selectedChannel)
                    for (channel in channels) {
                        if (repository.getPreviewPath(channel.uri) != null) continue
                        val bitmap = scanner.createPreview(channel) ?: continue
                        storePreview(channel.uri, bitmap, onlyIfMissing = true)
                    }
                }
        }
    }

    fun addRoot(uri: Uri) {
        viewModelScope.launch {
            repository.addRoot(uri)
        }
    }

    fun removeRoot(uri: String) {
        viewModelScope.launch {
            repository.removeRoot(uri)
        }
    }

    fun setRootEnabled(uri: String, enabled: Boolean) {
        viewModelScope.launch { repository.setRootEnabled(uri, enabled) }
    }

    fun recordWatchTime(channel: ChannelFolder, elapsedMs: Long) {
        viewModelScope.launch { repository.addWatchTime(channel, elapsedMs) }
    }

    fun selectRelativeChannel(offset: Int) {
        val channels = _uiState.value.channels
        val nextIndex = relativeChannelIndex(
            channelUris = channels.map(ChannelFolder::uri),
            currentChannelUri = _uiState.value.selectedChannel?.uri,
            offset = offset,
        ) ?: return
        selectChannel(channels[nextIndex].uri)
    }

    fun getPreviewPath(channelUri: String): String? = repository.getPreviewPath(channelUri)

    fun saveProgress(channelUri: String, videoUri: String?, videoIndex: Int, positionMs: Long): Job? {
        val channel = _uiState.value.channels.firstOrNull { it.uri == channelUri } ?: return null
        if (videoUri == null || videoIndex < 0) return null
        return viewModelScope.launch {
            progressSaveMutex.withLock {
                repository.saveProgress(
                    rootUri = channel.rootUri,
                    channelUri = channel.uri,
                    videoUri = videoUri,
                    videoIndex = videoIndex,
                    positionMs = positionMs,
                )
                _uiState.update { previous ->
                    val cached = previous.preparedChannels[channelUri]
                    val state = if (cached != null) previous.copy(
                        preparedChannels = previous.preparedChannels + (channelUri to cached.copy(
                            startVideoIndex = videoIndex,
                            startPositionMs = positionMs.coerceAtLeast(0),
                        )),
                    ) else previous
                    if (
                        state.selectedChannel?.uri == channelUri &&
                        state.videos.getOrNull(videoIndex)?.uri?.toString() == videoUri
                    ) {
                        state.copy(
                            startVideoIndex = videoIndex,
                            startPositionMs = positionMs.coerceAtLeast(0),
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun savePreview(channelUri: String, bitmap: Bitmap) {
        viewModelScope.launch {
            storePreview(channelUri, bitmap)
        }
    }

    private suspend fun storePreview(
        channelUri: String,
        bitmap: Bitmap,
        onlyIfMissing: Boolean = false,
    ) {
        try {
            previewSaveMutex.withLock {
                if (onlyIfMissing && repository.getPreviewPath(channelUri) != null) return@withLock
                val previewPath = repository.savePreview(channelUri, bitmap) ?: return@withLock
                _uiState.update { state ->
                    if (state.selectedChannel?.uri == channelUri) {
                        state.copy(
                            previewPath = previewPath,
                            previewUpdatedAt = File(previewPath).lastModified(),
                        )
                    } else {
                        state
                    }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun showMessage(message: String?) {
        _uiState.update { it.copy(message = message) }
    }

    fun setNormalizeAudio(enabled: Boolean) {
        repository.normalizeAudio = enabled
        _uiState.update { it.copy(normalizeAudio = enabled) }
    }

    fun setAudioLanguage(language: String) {
        repository.audioLanguage = language
        _uiState.update { it.copy(audioLanguage = language) }
    }

    fun setSubtitleLanguage(language: String) {
        repository.subtitleLanguage = language
        _uiState.update { it.copy(subtitleLanguage = language) }
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        repository.subtitlesEnabled = enabled
        _uiState.update { it.copy(subtitlesEnabled = enabled) }
    }

    fun setSubtitleFontSize(size: Int) {
        val percent = size.coerceIn(50, 200)
        repository.subtitleFontSize = percent
        _uiState.update { it.copy(subtitleFontSize = percent) }
    }

    fun selectChannel(uri: String) {
        val channel = _uiState.value.channels.firstOrNull { it.uri == uri } ?: return
        selectChannel(channel)
    }

    private fun selectChannel(channel: ChannelFolder?) {
        channelLoadJob?.cancel()
        if (channel == null) {
            _uiState.update {
                it.copy(
                    selectedChannel = null,
                    videos = emptyList(),
                    previewPath = null,
                    previewUpdatedAt = 0,
                    isLoading = false,
                    message = if (it.roots.isEmpty()) {
                        null
                    } else {
                        if (it.roots.none { root -> root.enabled }) {
                            "Enable a root folder to watch its channels"
                        } else {
                            "No channel folders found inside the configured roots"
                        }
                    },
                )
            }
            return
        }

        repository.selectedChannelUri = channel.uri
        val previewPath = repository.getPreviewPath(channel.uri)
        val cached = _uiState.value.preparedChannels[channel.uri]
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                videos = cached?.videos.orEmpty(),
                startVideoIndex = cached?.startVideoIndex ?: 0,
                startPositionMs = cached?.startPositionMs ?: 0,
                previewPath = previewPath,
                previewUpdatedAt = previewPath?.let { path -> File(path).lastModified() } ?: 0,
                isLoading = cached == null,
                message = if (cached?.videos?.isEmpty() == true) "No playable videos in this channel" else null,
            )
        }
        channelLoadJob = viewModelScope.launch {
            val playback = cached ?: loadChannel(channel)
            val currentPreviewPath = repository.getPreviewPath(channel.uri)
            _uiState.update {
                it.copy(
                    videos = playback.videos,
                    startVideoIndex = playback.startVideoIndex,
                    startPositionMs = playback.startPositionMs,
                    preparedChannels = it.preparedChannels + (channel.uri to playback),
                    previewPath = currentPreviewPath,
                    previewUpdatedAt = currentPreviewPath?.let { path -> File(path).lastModified() } ?: 0,
                    isLoading = false,
                    message = if (playback.videos.isEmpty()) "No playable videos in this channel" else null,
                )
            }
            val neighbors = neighborChannelUris(_uiState.value.channels.map { it.uri }, channel.uri)
            val retained = neighbors + channel.uri
            _uiState.update { it.copy(preparedChannels = it.preparedChannels.filterKeys { uri -> uri in retained }) }
            for (uri in neighbors) {
                if (_uiState.value.preparedChannels.containsKey(uri)) continue
                val neighbor = _uiState.value.channels.firstOrNull { it.uri == uri } ?: continue
                val prepared = loadChannel(neighbor)
                _uiState.update { it.copy(preparedChannels = it.preparedChannels + (uri to prepared)) }
            }
        }
    }

    private suspend fun loadChannel(channel: ChannelFolder): ChannelPlayback {
        val progress = repository.getProgress(channel.uri)
        val videos = scanner.scan(channel)
        val resumePoint = resolveResumePoint(
            videoUris = videos.map { it.uri.toString() },
            savedVideoUri = progress?.currentVideoUri,
            savedVideoIndex = progress?.currentVideoIndex ?: 0,
            savedPositionMs = progress?.positionMs ?: 0,
        )
        return ChannelPlayback(videos, resumePoint.videoIndex, resumePoint.positionMs)
    }

    override fun onCleared() {
        channelLoadJob?.cancel()
        super.onCleared()
    }
}

class MainViewModelFactory(
    private val application: KidsChannelApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java))
        return MainViewModel(
            repository = application.rootRepository,
            scanner = application.channelScanner,
        ) as T
    }
}
