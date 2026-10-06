package com.thekidschannel.ui

import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thekidschannel.ChannelPlayback
import com.thekidschannel.MainActivity
import com.thekidschannel.MainUiState
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreparedChannelPlayersTest {
    @Test
    fun keepsThePausedPreviewWhenTheVideoSurfaceIsRecreated() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "paused-preview-test.jpg")
        val image = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.GREEN)
        file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        image.recycle()
        val paused = mutableStateOf(false)
        val ready = mutableStateOf(true)
        val applied = AtomicReference(CountDownLatch(1))
        val channel = ChannelFolder("root", "channel", "Channel")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        val isPaused = paused.value
                        val hasFrame = ready.value
                        PreparedPlayerScreen(
                            MainUiState(selectedChannel = channel, channels = listOf(channel),
                                previewPath = file.absolutePath, isLoading = false),
                            {}, { null },
                        ) { _, _ ->
                            ChannelPlayerControls(
                                isPaused = { isPaused },
                                hasPreparedFrame = { hasFrame },
                                hasRenderedFirstFrame = { hasFrame },
                                togglePlayback = {},
                                prepareChannelChange = {},
                                openSettings = {},
                                videoSurface = { _, visible ->
                                    if (visible) Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Red))
                                },
                            )
                        }
                        SideEffect { applied.get().countDown() }
                    }
                }
                assertTrue(applied.get().await(10, TimeUnit.SECONDS))
                assertScreenColor(Color.RED)
                applied.set(CountDownLatch(1))
                scenario.onActivity { paused.value = true; ready.value = false }
                assertTrue(applied.get().await(10, TimeUnit.SECONDS))
                assertScreenColor(Color.GREEN)
                applied.set(CountDownLatch(1))
                scenario.onActivity { paused.value = false }
                assertTrue(applied.get().await(10, TimeUnit.SECONDS))
                assertScreenColor(Color.GREEN)
                applied.set(CountDownLatch(1))
                scenario.onActivity { ready.value = true }
                assertTrue(applied.get().await(10, TimeUnit.SECONDS))
                assertScreenColor(Color.RED)
            }
        } finally {
            file.delete()
        }
    }

    private fun assertScreenColor(expected: Int) {
        Thread.sleep(300)
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            val actual = screenshot.getPixel(screenshot.width / 2, screenshot.height / 4)
            assertEquals(Color.red(expected).toFloat(), Color.red(actual).toFloat(), 3f)
            assertEquals(Color.green(expected).toFloat(), Color.green(actual).toFloat(), 3f)
            assertEquals(Color.blue(expected).toFloat(), Color.blue(actual).toFloat(), 3f)
        } finally {
            screenshot.recycle()
        }
    }

    @Test
    fun switchingToAnUnpreparedChannelRetainsItsPreviousPlayer() {
        val channels = ('A'..'D').map { ChannelFolder("root", it.toString(), it.toString()) }
        val videos = listOf(VideoItem(Uri.parse("content://test/video"), "video"))
        val state = mutableStateOf(MainUiState(
            channels = channels,
            selectedChannel = channels[1],
            videos = videos,
            preparedChannels = channels.associate { it.uri to ChannelPlayback(videos, 0, 0) },
            isLoading = false,
        ))
        val frames = mutableStateMapOf("B" to true)
        val created = mutableMapOf<String, Int>()
        val disposed = mutableMapOf<String, Int>()
        val applied = AtomicReference(CountDownLatch(1))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    PreparedPlayerScreen(state.value, {}, { null }) { channelState, _ ->
                        val uri = channelState.selectedChannel!!.uri
                        DisposableEffect(uri) {
                            created[uri] = (created[uri] ?: 0) + 1
                            onDispose { disposed[uri] = (disposed[uri] ?: 0) + 1 }
                        }
                        ChannelPlayerControls(
                            isPaused = { false },
                            hasPreparedFrame = { frames[uri] == true },
                            hasRenderedFirstFrame = { frames[uri] == true },
                            togglePlayback = {},
                            prepareChannelChange = {},
                            openSettings = {},
                            videoSurface = { _, _ -> },
                        )
                    }
                    SideEffect { applied.get().countDown() }
                }
            }
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            applied.set(CountDownLatch(1))
            scenario.onActivity { state.value = state.value.copy(selectedChannel = channels[2]) }
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            assertEquals(1, created["B"])
            assertEquals(null, disposed["B"])
            assertEquals(1, disposed["A"])

            applied.set(CountDownLatch(1))
            scenario.onActivity { state.value = state.value.copy(selectedChannel = channels[1]) }
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            assertEquals(1, created["B"])
            assertEquals(1, created["C"])
            assertEquals(null, disposed["B"])
        }
    }
}
