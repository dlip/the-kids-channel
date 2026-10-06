package com.thekidschannel.ui

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.thekidschannel.MainUiState
import com.thekidschannel.R
import com.thekidschannel.media.PlaybackFrameGate
import com.thekidschannel.media.ChannelFolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    state: MainUiState,
    onSelectChannel: (String) -> Unit,
    onChannelPreviewPath: suspend (String) -> String?,
    onSaveProgress: (String, String?, Int, Long) -> Job?,
    onSavePreview: (String, Bitmap) -> Unit,
    onRecordWatchTime: (ChannelFolder, Long) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
) {
    PreparedPlayerScreen(state, onSelectChannel, onChannelPreviewPath) { channelState, active ->
        rememberChannelPlayer(
            channelState, active, onSaveProgress, onSavePreview,
            onRecordWatchTime, onSettings, onPlaybackMessage,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun rememberChannelPlayer(
    state: MainUiState,
    active: Boolean,
    onSaveProgress: (String, String?, Int, Long) -> Job?,
    onSavePreview: (String, Bitmap) -> Unit,
    onRecordWatchTime: (ChannelFolder, Long) -> Unit,
    onSettings: () -> Unit,
    onPlaybackMessage: (String?) -> Unit,
): ChannelPlayerControls {
    val isActive by rememberUpdatedState(active)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val channelUri = state.selectedChannel?.uri
    val player = remember(channelUri, state.normalizeAudio) {
        val renderersFactory = NormalizingRenderersFactory(
            context,
            state.normalizeAudio,
        ).apply {
            setEnableDecoderFallback(true)
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 10_000, 250, 500)
                .build())
            .build()
    }
    LaunchedEffect(player, state.audioLanguage, state.subtitleLanguage, state.subtitlesEnabled) {
        player.applyLanguagePreferences(state)
    }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    val frameCaptureMutex = remember { Mutex() }
    var hasRenderedFirstFrame by remember(channelUri) { mutableStateOf(false) }
    val playbackFrameState by rememberUpdatedState(
        remember(player, active) { mutableStateOf(false) },
    )
    val playbackFrameGate by rememberUpdatedState(
        remember(player, active) { PlaybackFrameGate(player.currentPosition.coerceAtLeast(0)) },
    )
    var failedItems by remember(channelUri) { mutableStateOf(emptySet<Int>()) }
    var isPaused by remember(channelUri) { mutableStateOf(false) }
    var playingChannelUri by remember(player) { mutableStateOf<String?>(null) }
    var activelyPlaying by remember(player, channelUri) { mutableStateOf(false) }

    TrackWatchTime(state.selectedChannel, active && activelyPlaying, onRecordWatchTime)

    fun persistProgress(): Job? {
        if (!isActive) return null
        val videoUri = player.currentMediaItem?.mediaId
        return onSaveProgress(
            playingChannelUri ?: return null,
            videoUri,
            player.currentMediaItemIndex,
            player.currentPosition,
        )
    }

    suspend fun captureAndSavePreview(requireActive: Boolean = true) {
        if ((requireActive && !isActive) || player.isPlaying) return
        val videoUri = player.currentMediaItem?.mediaId ?: return
        val previewChannelUri = playingChannelUri ?: return
        if (channelUri != previewChannelUri) return
        val previewSource = playerView ?: return
        if (!hasRenderedFirstFrame) return
        frameCaptureMutex.withLock {
            if (
                channelUri != previewChannelUri ||
                playingChannelUri != previewChannelUri ||
                player.currentMediaItem?.mediaId != videoUri ||
                !hasRenderedFirstFrame
            ) {
                return@withLock
            }
            val bitmap = captureVideoFrame(previewSource) ?: return@withLock
            if (
                playingChannelUri != previewChannelUri || playerView !== previewSource ||
                player.currentMediaItem?.mediaId != videoUri || !hasRenderedFirstFrame || player.isPlaying
            ) {
                bitmap.recycle()
                return@withLock
            }
            onSavePreview(previewChannelUri, bitmap)
        }
    }

    fun saveProgress() {
        persistProgress()
    }

    suspend fun prepareChannelChange() {
        persistProgress()
        player.pause()
        coroutineScope.launch { captureAndSavePreview(requireActive = false) }
    }

    LaunchedEffect(player, channelUri, state.videos) {
        hasRenderedFirstFrame = false
        playbackFrameState.value = false
        playbackFrameGate.reset(state.startPositionMs.coerceAtLeast(0))
        if (state.videos.isEmpty()) {
            playingChannelUri = null
            player.clearMediaItems()
            return@LaunchedEffect
        }
        val items = state.videos.map { video ->
            MediaItem.Builder()
                .setUri(video.uri)
                .setMediaId(video.uri.toString())
                .build()
        }
        player.setMediaItems(items, state.startVideoIndex, state.startPositionMs)
        playingChannelUri = channelUri
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.prepare()
        player.playWhenReady = isActive && foreground
        if (isActive) onPlaybackMessage(null)
    }

    LaunchedEffect(player, active) {
        player.volume = if (active) 1f else 0f
        if (active && foreground) {
            isPaused = false
            if (player.mediaItemCount > 0) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            }
        } else {
            player.pause()
        }
    }

    DisposableEffect(player, channelUri) {
        var listening = true
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!listening || playingChannelUri != channelUri) return
                hasRenderedFirstFrame = false
                playbackFrameState.value = false
                playbackFrameGate.reset(player.currentPosition.coerceAtLeast(0))
                saveProgress()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!listening || playingChannelUri != channelUri) return
                if (isActive && foreground) isPaused = !playWhenReady
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!listening || playingChannelUri != channelUri) return
                activelyPlaying = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!listening || playingChannelUri != channelUri) return
                if (!isActive) {
                    player.stop()
                    hasRenderedFirstFrame = false
                    return
                }
                val failedIndex = player.currentMediaItemIndex
                failedItems = failedItems + failedIndex
                val nextIndex = (1..player.mediaItemCount)
                    .map { (failedIndex + it) % player.mediaItemCount.coerceAtLeast(1) }
                    .firstOrNull { it !in failedItems }
                if (nextIndex == null || player.mediaItemCount == 0) {
                    onPlaybackMessage("None of this channel's videos could be played")
                    return
                }
                player.seekToDefaultPosition(nextIndex)
                player.prepare()
                player.playWhenReady = true
            }
        }
        player.addListener(listener)
        onDispose {
            listening = false
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, active, isPaused, activelyPlaying) {
        if (active && isPaused && !activelyPlaying) captureAndSavePreview()
    }

    DisposableEffect(lifecycleOwner, player, channelUri) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    if (isActive && player.mediaItemCount > 0 && !isPaused) player.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    saveProgress()
                    player.pause()
                    hasRenderedFirstFrame = false
                    playbackFrameState.value = false
                    playbackFrameGate.reset(player.currentPosition.coerceAtLeast(0))
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(player, channelUri) {
        while (true) {
            delay(5_000)
            saveProgress()
        }
    }

    DisposableEffect(player) {
        onDispose {
            saveProgress()
            player.release()
        }
    }

    return ChannelPlayerControls(
        isPaused = { isPaused },
        hasPreparedFrame = { hasRenderedFirstFrame },
        hasRenderedFirstFrame = { if (isActive) playbackFrameState.value else hasRenderedFirstFrame },
        togglePlayback = {
            if (player.playWhenReady) player.pause() else player.play()
        },
        prepareChannelChange = ::prepareChannelChange,
        openSettings = {
            coroutineScope.launch {
                persistProgress()?.join()
                onSettings()
            }
        },
        videoSurface = { offset, visible ->
            key(player) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        (LayoutInflater.from(viewContext).inflate(
                            R.layout.standard_player_view,
                            null,
                        ) as PlayerView).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            alpha = if (visible) 1f else 0f
                            translationY = offset()
                            this.player = player
                            subtitleView?.applyFontSize(state.subtitleFontSize)
                            playerView = this
                            val texture = videoSurfaceView as TextureView
                            val listener = checkNotNull(texture.surfaceTextureListener)
                            texture.surfaceTextureListener = object :
                                TextureView.SurfaceTextureListener by listener {
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                    listener.onSurfaceTextureUpdated(surface)
                                    if (playerView === this@apply && playingChannelUri == channelUri) {
                                        hasRenderedFirstFrame = true
                                        if (isActive) playbackFrameState.value = playbackFrameGate.onFrame(
                                            player.currentPosition.coerceAtLeast(0), player.isPlaying,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    update = {
                        it.player = player
                        it.subtitleView?.applyFontSize(state.subtitleFontSize)
                        it.alpha = if (visible) 1f else 0f
                        it.translationY = offset()
                    },
                    onRelease = { it.player = null },
                )
            }
        },
    )
}
