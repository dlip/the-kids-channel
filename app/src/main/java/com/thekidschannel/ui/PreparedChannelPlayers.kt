package com.thekidschannel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.thekidschannel.MainUiState
import com.thekidschannel.media.neighborChannelUris

internal data class ChannelPlayerControls(
    val isPaused: () -> Boolean,
    val hasPreparedFrame: () -> Boolean,
    val hasRenderedFirstFrame: () -> Boolean,
    val togglePlayback: () -> Unit,
    val prepareChannelChange: suspend () -> Unit,
    val openSettings: () -> Unit,
    val videoSurface: @Composable (() -> Float, Boolean) -> Unit,
)

@Composable
internal fun PreparedPlayerScreen(
    state: MainUiState,
    onSelectChannel: (String) -> Unit,
    onChannelPreviewPath: suspend (String) -> String?,
    preparePlayer: @Composable (MainUiState, Boolean) -> ChannelPlayerControls,
) {
    val currentUri = state.selectedChannel?.uri ?: return
    val neighborUris = neighborChannelUris(state.channels.map { it.uri }, currentUri)
    val players = linkedMapOf<String, ChannelPlayerControls>()
    var currentFrameReady = false
    state.channels.filter { it.uri == currentUri || it.uri in neighborUris }
        .sortedBy { it.uri != currentUri }
        .forEach { channel ->
            val active = channel.uri == currentUri
            val playback = state.preparedChannels[channel.uri]
            val player = key(channel.uri, state.normalizeAudio) {
                // A cold selection must not dispose neighbors that are already warm.
                var started by remember { mutableStateOf(false) }
                if (active || started || (currentFrameReady && playback?.videos?.isNotEmpty() == true)) {
                    SideEffect { started = true }
                    val channelState = if (active) state else state.copy(
                        selectedChannel = channel,
                        videos = playback?.videos.orEmpty(),
                        startVideoIndex = playback?.startVideoIndex ?: 0,
                        startPositionMs = playback?.startPositionMs ?: 0,
                        isLoading = false,
                        message = null,
                    )
                    preparePlayer(channelState, active)
                } else null
            }
            if (player != null) {
                players[channel.uri] = player
                if (active) currentFrameReady = player.hasPreparedFrame()
            }
        }
    val current = players[currentUri] ?: return
    val preparedAtEntry = remember(currentUri, state.normalizeAudio) { current.hasPreparedFrame() }
    val view = LocalView.current
    val isPaused = current.isPaused()
    SideEffect { view.keepScreenOn = !isPaused }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }
    PlayerScreenLayout(
        state = state,
        isPaused = isPaused,
        showPreview = !(preparedAtEntry && current.hasPreparedFrame()) && !current.hasRenderedFirstFrame(),
        isPlaybackReady = current.hasRenderedFirstFrame(),
        onTogglePlayback = current.togglePlayback,
        onPrepareChannelChange = current.prepareChannelChange,
        onSelectChannel = onSelectChannel,
        onChannelPreviewPath = onChannelPreviewPath,
        onSettings = current.openSettings,
        hasPreparedVideo = { uri -> players[uri]?.hasPreparedFrame() == true },
        videoSurface = { offset, incomingUri, incomingOffset ->
            Box(Modifier.fillMaxSize()) {
                players.forEach { (uri, player) ->
                    key(uri, state.normalizeAudio) {
                        Box(Modifier.fillMaxSize()) {
                            val visible = uri == currentUri ||
                                (uri == incomingUri && player.hasPreparedFrame())
                            player.videoSurface(
                                if (uri == incomingUri) incomingOffset else offset,
                                visible,
                            )
                        }
                    }
                }
            }
        },
    )
}
