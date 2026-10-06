package com.thekidschannel.ui

import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.thekidschannel.MainUiState
import com.thekidschannel.media.relativeChannelIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private data class SwipeTarget(
    val uri: String,
    val direction: Int,
    val name: String,
    val preview: ImageBitmap?,
)

@Composable
internal fun PlayerScreenLayout(
    state: MainUiState,
    isPaused: Boolean,
    showPreview: Boolean,
    isPlaybackReady: Boolean,
    onTogglePlayback: () -> Unit,
    onPrepareChannelChange: suspend () -> Unit,
    onSelectChannel: (String) -> Unit,
    onChannelPreviewPath: suspend (String) -> String?,
    onSettings: () -> Unit,
    hasPreparedVideo: (String) -> Boolean,
    videoSurface: @Composable (() -> Float, String?, () -> Float) -> Unit,
) {
    val debugLogging = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val channelUri = state.selectedChannel?.uri
    var holdingForSettings by remember { mutableStateOf(false) }
    var channelTitleBounds by remember { mutableStateOf<Rect?>(null) }
    val holdProgress = remember { Animatable(0f) }
    var holdTargetFraction by remember { mutableFloatStateOf(0.5f) }
    var blockPauseUntil by remember { mutableLongStateOf(0L) }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var swipeResetJob by remember { mutableStateOf<Job?>(null) }
    var swipeTarget by remember { mutableStateOf<SwipeTarget?>(null) }
    var adjacentPreviews by remember(channelUri) {
        mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    }
    var channelChangeInProgress by remember { mutableStateOf(false) }
    var channelChangeGeneration by remember { mutableIntStateOf(0) }
    var transitionFinishedAtMs by remember { mutableLongStateOf(0L) }
    var pendingChannelUri by remember { mutableStateOf<String?>(null) }
    var channelSlide by remember { mutableStateOf<SwipeTarget?>(null) }
    val channelLabelVisible = isPaused || swipeTarget != null ||
        channelChangeInProgress || pendingChannelUri != null || channelSlide != null
    val currentChannelLabelVisible by rememberUpdatedState(channelLabelVisible)
    var keepPreviewVisible by remember(channelUri) { mutableStateOf(showPreview || state.isLoading) }
    val coroutineScope = rememberCoroutineScope()
    val currentChannelPreviewPath by rememberUpdatedState(onChannelPreviewPath)
    val preview = remember(
        state.previewPath,
        state.previewUpdatedAt,
    ) {
        state.previewPath
            ?.let(BitmapFactory::decodeFile)
            ?.asImageBitmap()
    }
    val pausedPreview = isPaused && showPreview && preview != null
    val previewVisible = state.isLoading || pausedPreview ||
        (keepPreviewVisible && (showPreview || preview != null))
    val waitingForVideo =
        (channelChangeInProgress && transitionFinishedAtMs != 0L) ||
        (!isPlaybackReady && !isPaused) || state.isLoading
    var loadingIndicatorVisible by remember(channelUri) { mutableStateOf(false) }

    LaunchedEffect(channelUri, waitingForVideo, transitionFinishedAtMs) {
        loadingIndicatorVisible = false
        if (waitingForVideo) {
            val elapsed = if (transitionFinishedAtMs == 0L) 0L else
                SystemClock.uptimeMillis() - transitionFinishedAtMs
            delay((200L - elapsed).coerceAtLeast(0L))
            loadingIndicatorVisible = true
        }
    }

    LaunchedEffect(
        channelUri,
        state.previewPath,
        state.previewUpdatedAt,
    ) {
        val image = preview ?: return@LaunchedEffect
        val currentUri = channelUri ?: return@LaunchedEffect
        adjacentPreviews = adjacentPreviews + (currentUri to image)
        if (swipeTarget?.uri == currentUri) {
            swipeTarget = swipeTarget?.copy(preview = image)
        }
        if (channelSlide?.uri == currentUri && channelSlide?.preview == null) {
            channelSlide = channelSlide?.copy(preview = image)
        }
    }

    suspend fun loadAdjacentPreview(previewChannelUri: String) {
        val image = currentChannelPreviewPath(previewChannelUri)?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        } ?: return
        adjacentPreviews = adjacentPreviews + (previewChannelUri to image)
        if (swipeTarget?.uri == previewChannelUri) {
            swipeTarget = swipeTarget?.copy(preview = image)
        }
        if (channelSlide?.uri == previewChannelUri) {
            channelSlide = channelSlide?.copy(preview = image)
        }
    }

    LaunchedEffect(channelUri, state.channels) {
        val neighbors = listOf(-1, 1).mapNotNull { direction ->
            relativeChannelIndex(
                channelUris = state.channels.map { it.uri },
                currentChannelUri = channelUri,
                offset = direction,
            )?.let(state.channels::get)
        }.distinctBy { it.uri }.filter { it.uri != channelUri }
        neighbors.forEach { channel -> loadAdjacentPreview(channel.uri) }
    }

    LaunchedEffect(channelUri, state.isLoading, showPreview, pausedPreview) {
        if (state.isLoading || pausedPreview) {
            keepPreviewVisible = true
            return@LaunchedEffect
        }
        // Once this channel is visible, a later video in its playlist must not
        // bring the channel-entry preview back over the playing video.
        if (!showPreview) keepPreviewVisible = false
    }

    fun targetForOffset(offset: Int, animationDirection: Int): SwipeTarget? {
        if (state.channels.size < 2) return null
        val index = relativeChannelIndex(
            channelUris = state.channels.map { it.uri },
            currentChannelUri = channelUri,
            offset = offset,
        ) ?: return null
        val channel = state.channels[index]
        return SwipeTarget(
            uri = channel.uri,
            direction = animationDirection,
            name = channel.name,
            preview = adjacentPreviews[channel.uri],
        )
    }

    fun changeChannel(target: SwipeTarget, heightPx: Float) {
        if (channelChangeInProgress) return
        channelChangeInProgress = true
        transitionFinishedAtMs = 0L
        channelChangeGeneration += 1
        val transitionGeneration = channelChangeGeneration
        coroutineScope.launch {
            try {
                val destination = if (target.direction > 0) -1f else 1f
                val startOffset = swipeOffset
                if (swipeTarget?.uri != target.uri) swipeTarget = target
                Animatable(startOffset).animateTo(
                    targetValue = destination * heightPx,
                    animationSpec = tween(durationMillis = CHANNEL_SLIDE_MS,
                        easing = FastOutSlowInEasing),
                ) { swipeOffset = value }
                transitionFinishedAtMs = SystemClock.uptimeMillis()
                val preparedAtMs = SystemClock.uptimeMillis()
                withTimeoutOrNull(CHANNEL_PREPARE_TIMEOUT_MS) {
                    onPrepareChannelChange()
                }
                val preparedVideo = hasPreparedVideo(target.uri)
                if (debugLogging) {
                    Log.d("ChannelTransition", "channel=${target.name} " +
                        "prepareMs=${SystemClock.uptimeMillis() - preparedAtMs} prepared=$preparedVideo")
                }
                pendingChannelUri = target.uri
                channelSlide = if (preparedVideo) null else
                    (swipeTarget?.takeIf { it.uri == target.uri } ?: target)
                swipeOffset = 0f
                swipeTarget = null
                onSelectChannel(target.uri)
                delay(CHANNEL_CHANGE_WATCHDOG_MS)
                if (channelChangeGeneration == transitionGeneration &&
                    pendingChannelUri == target.uri
                ) {
                    channelSlide = null
                    pendingChannelUri = null
                    channelChangeInProgress = false
                }
            } catch (error: Throwable) {
                if (channelChangeGeneration == transitionGeneration) {
                    channelSlide = null
                    pendingChannelUri = null
                    channelChangeInProgress = false
                    swipeOffset = 0f
                    swipeTarget = null
                }
                throw error
            }
        }
    }

    LaunchedEffect(channelUri, state.isLoading, keepPreviewVisible, state.message) {
        if (pendingChannelUri != null &&
            !state.isLoading &&
            (channelUri != pendingChannelUri || state.message != null)
        ) {
            channelSlide = null
            pendingChannelUri = null
            channelChangeInProgress = false
        } else if (pendingChannelUri == channelUri) {
            // Allow another swipe as soon as selection changes, even while its playlist loads.
            channelChangeInProgress = false
            if (!state.isLoading && !keepPreviewVisible) {
                channelSlide = null
                pendingChannelUri = null
            }
        }
    }

    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentLoadAdjacentPreview by rememberUpdatedState<suspend (String) -> Unit>(
        newValue = { uri -> loadAdjacentPreview(uri) },
    )
    val currentTogglePlayback by rememberUpdatedState(onTogglePlayback)
    val currentSettings by rememberUpdatedState(onSettings)
    // Fresh lambdas keep gesture callbacks tied to the newly selected channel.
    val currentSwipeTarget by rememberUpdatedState<(Int, Int) -> SwipeTarget?>(
        newValue = { offset, direction -> targetForOffset(offset, direction) },
    )
    val currentChangeChannel by rememberUpdatedState<(SwipeTarget, Float) -> Unit>(
        newValue = { target, height -> changeChannel(target, height) },
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color.Black),
    ) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val incoming = swipeTarget
        videoSurface(
            { swipeOffset },
            incoming?.uri,
            { swipeOffset + (incoming?.direction ?: 0) * heightPx },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = swipeOffset },
        ) {
            key(channelUri) {
                AnimatedVisibility(
                    visible = previewVisible,
                    enter = EnterTransition.None,
                    exit = fadeOut(tween(durationMillis = PREVIEW_FADE_OUT_MS)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                    ) {
                        preview?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            channelSlide?.let { slide ->
                ChannelSlidePanel(
                    name = slide.name,
                    preview = slide.preview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        swipeTarget?.takeUnless { hasPreparedVideo(it.uri) }?.let { target ->
            ChannelSlidePanel(
                name = target.name,
                preview = target.preview,
                modifier = Modifier.graphicsLayer {
                    translationY = swipeOffset + target.direction * heightPx
                },
            )
        }

        if (!state.isLoading && state.message != null) {
            Text(
                text = state.message,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (
            waitingForVideo && loadingIndicatorVisible && state.message == null &&
            (swipeTarget == null || transitionFinishedAtMs != 0L)
        ) {
            CircularProgressIndicator(
                color = Color(0xFF2196F3),
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .semantics { contentDescription = "Loading video" },
            )
        }

        AnimatedVisibility(
            visible = channelLabelVisible,
            enter = fadeIn(tween(durationMillis = 200)),
            exit = fadeOut(tween(durationMillis = 500)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .drawBehind {
                        val progress = if (holdingForSettings) holdProgress.value else 0f
                        val targetX = size.width * holdTargetFraction
                        val leftWidth = targetX * progress
                        val rightWidth = (size.width - targetX) * progress
                        drawRect(
                            color = Color.Red,
                            size = Size(leftWidth, size.height),
                        )
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset(size.width - rightWidth, 0f),
                            size = Size(rightWidth, size.height),
                        )
                    }
                    .onGloballyPositioned { channelTitleBounds = it.boundsInRoot() },
            ) {
                Text(
                    text = state.selectedChannel?.name.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (
            isPaused && !channelChangeInProgress && swipeTarget == null &&
            pendingChannelUri == null && channelSlide == null
        ) {
            val pauseShape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(pauseShape)
                    .background(Color.Black.copy(alpha = 0.55f), pauseShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    tint = Color.White,
                    modifier = Modifier.padding(24.dp).size(64.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Tap to pause or resume. Swipe up or down to change " +
                        "channels. While paused, hold the upper-left channel title for two seconds for settings."
                    onClick(label = if (isPaused) "Resume video" else "Pause video") {
                        if (!channelChangeInProgress && swipeTarget == null &&
                            swipeResetJob?.isActive != true &&
                            SystemClock.uptimeMillis() >= blockPauseUntil
                        ) currentTogglePlayback()
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction("Next channel") {
                            currentSwipeTarget(1, 1)?.let {
                                currentChangeChannel(it, heightPx)
                            }
                            true
                        },
                        CustomAccessibilityAction("Previous channel") {
                            currentSwipeTarget(-1, -1)?.let {
                                currentChangeChannel(it, heightPx)
                            }
                            true
                        },
                        CustomAccessibilityAction("Open settings") {
                            if (isPaused) {
                                currentSettings()
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    try {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            swipeResetJob?.cancel()
                            if (!channelChangeInProgress) {
                                swipeOffset = 0f
                                swipeTarget = null
                            }
                            down.consume()
                            val startedDuringTransition = channelChangeInProgress
                            var waitingForTransition = startedDuringTransition
                            var dragOrigin = down.position
                            val holdOnChannelTitle = currentIsPaused && currentChannelLabelVisible &&
                                channelTitleBounds?.contains(down.position) == true &&
                                !channelChangeInProgress &&
                                SystemClock.uptimeMillis() >= blockPauseUntil
                            var holdCompleted = false
                            val holdJob = if (holdOnChannelTitle) coroutineScope.launch {
                                channelTitleBounds?.let { bounds ->
                                    holdTargetFraction = ((down.position.x - bounds.left) / bounds.width)
                                        .coerceIn(0f, 1f)
                                }
                                holdProgress.snapTo(0f)
                                holdingForSettings = true
                                holdProgress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = SETTINGS_HOLD_MS,
                                        easing = LinearEasing,
                                    ),
                                )
                                holdCompleted = true
                                currentSettings()
                            } else null
                            var totalX = 0f
                            var totalY = 0f
                            var moved = false
                            var target: SwipeTarget? = null
                            var released = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (channelChangeInProgress) {
                                        waitingForTransition = true
                                        dragOrigin = change.position
                                        if (!change.pressed) {
                                            released = true
                                            break
                                        }
                                        change.consume()
                                        continue
                                    }
                                    if (waitingForTransition) {
                                        dragOrigin = change.previousPosition
                                        waitingForTransition = false
                                    }
                                    totalX = change.position.x - dragOrigin.x
                                    totalY = change.position.y - dragOrigin.y
                                    if (!moved &&
                                        (abs(totalX) > viewConfiguration.touchSlop ||
                                            abs(totalY) > viewConfiguration.touchSlop)
                                    ) {
                                        moved = true
                                        blockPauseUntil = SystemClock.uptimeMillis() +
                                            POST_SWIPE_PAUSE_BLOCK_MS
                                        holdJob?.cancel()
                                        holdingForSettings = false
                                        if (abs(totalY) > abs(totalX) &&
                                            !channelChangeInProgress
                                        ) {
                                            val channelOffset = if (totalY < 0) 1 else -1
                                            val animationDirection = channelOffset
                                            target = currentSwipeTarget(
                                                channelOffset,
                                                animationDirection,
                                            )
                                            if (target != null) {
                                                channelSlide = null
                                                pendingChannelUri = null
                                            }
                                            swipeTarget = target
                                            if (target?.preview == null) {
                                                target?.uri?.let { previewChannelUri ->
                                                    coroutineScope.launch {
                                                        currentLoadAdjacentPreview(previewChannelUri)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (target != null) {
                                        val offset = if (target.direction > 0) {
                                            totalY.coerceIn(-size.height.toFloat(), 0f)
                                        } else {
                                            totalY.coerceIn(0f, size.height.toFloat())
                                        }
                                        swipeOffset = offset
                                        change.consume()
                                    }
                                    if (!change.pressed) {
                                        released = true
                                        break
                                    }
                                }
                            } finally {
                                holdJob?.cancel()
                                holdingForSettings = false
                            }
                            if (released && target != null) {
                                val threshold = size.height * CHANNEL_CHANGE_THRESHOLD
                                if (abs(swipeOffset) >= threshold) {
                                    currentChangeChannel(target, size.height.toFloat())
                                } else {
                                    swipeResetJob = coroutineScope.launch {
                                        Animatable(swipeOffset).animateTo(
                                            0f,
                                            tween(durationMillis = 180),
                                        ) { swipeOffset = value }
                                        swipeTarget = null
                                        blockPauseUntil = SystemClock.uptimeMillis() +
                                            POST_SWIPE_PAUSE_BLOCK_MS
                                    }
                                }
                            } else if (released && !moved && !holdCompleted &&
                                !startedDuringTransition &&
                                !channelChangeInProgress &&
                                swipeResetJob?.isActive != true &&
                                SystemClock.uptimeMillis() >= blockPauseUntil &&
                                eventTimeIsTap(down.uptimeMillis, SystemClock.uptimeMillis())
                            ) {
                                if (!holdOnChannelTitle) currentTogglePlayback()
                            } else if (!released && target != null) {
                                swipeOffset = 0f
                                swipeTarget = null
                            }
                        }
                    } finally {
                        swipeResetJob?.cancel()
                        swipeOffset = 0f
                        swipeTarget = null
                        holdingForSettings = false
                    }
                },
        )
    }
}

private const val CHANNEL_PREPARE_TIMEOUT_MS = 1_000L
private const val CHANNEL_CHANGE_WATCHDOG_MS = 5_000L
private const val CHANNEL_SLIDE_MS = 150
private const val CHANNEL_CHANGE_THRESHOLD = 0.1f
private const val PREVIEW_FADE_OUT_MS = 100
private const val SETTINGS_HOLD_MS = 2_000
private const val POST_SWIPE_PAUSE_BLOCK_MS = 300L

private fun eventTimeIsTap(downTime: Long, upTime: Long): Boolean =
    upTime - downTime <= 500L

@Composable
private fun ChannelSlidePanel(name: String, preview: ImageBitmap?, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (preview == null) {
            Text(
                text = name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
        } else {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
