package com.thekidschannel.ui

import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.ChannelPlayback
import com.thekidschannel.MainActivity
import com.thekidschannel.MainUiState
import com.thekidschannel.media.ChannelFolder
import com.thekidschannel.media.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FilmstripNavigationTest {
    @Test
    fun swipesAndPausedFilmstripBothChangeChannels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val channels = ('A'..'D').map { ChannelFolder("root", it.toString(), "Channel $it") }
        val videos = listOf(VideoItem(Uri.parse("content://test/video"), "video"))
        val state = mutableStateOf(MainUiState(
            channels = channels,
            selectedChannel = channels[0],
            videos = videos,
            preparedChannels = channels.associate { it.uri to ChannelPlayback(videos, 0, 0) },
            isLoading = false,
        ))
        val paused = mutableStateOf(false)
        val preparedChanges = AtomicInteger()
        val applied = AtomicReference(CountDownLatch(1))
        val bounds = Rect()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    PreparedPlayerScreen(
                        state.value,
                        { uri ->
                            paused.value = false
                            state.value = state.value.copy(selectedChannel = channels.first { it.uri == uri })
                        },
                        { null },
                    ) { channelState, _ ->
                        ChannelPlayerControls(
                            isPaused = { paused.value },
                            hasPreparedFrame = { true },
                            hasRenderedFirstFrame = { true },
                            togglePlayback = { paused.value = !paused.value },
                            prepareChannelChange = { preparedChanges.incrementAndGet() },
                            openSettings = {},
                            videoSurface = { _, _ -> },
                        )
                    }
                    SideEffect { applied.get().countDown() }
                }
            }
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val focusDeadline = SystemClock.uptimeMillis() + 5_000
            var focused = false
            while (!focused && SystemClock.uptimeMillis() < focusDeadline) {
                scenario.onActivity { focused = it.hasWindowFocus() }
                if (!focused) SystemClock.sleep(50)
            }
            assertTrue("The player must have focus before sending gestures", focused)
            instrumentation.uiAutomation.waitForIdle(300, 5_000)
            scenario.onActivity { it.window.decorView.getGlobalVisibleRect(bounds) }

            val x = bounds.exactCenterX()
            val y = bounds.exactCenterY()
            touch(x, y, y - bounds.height() / 3)
            awaitChannel(state, "B")
            assertFalse(paused.value)

            touch(x, y, y + bounds.height() / 3)
            awaitChannel(state, "A")
            assertEquals("A", state.value.selectedChannel?.uri)
            assertFalse(paused.value)

            SystemClock.sleep(350)
            touch(x, y)
            awaitNode("Channel B")
            assertTrue(paused.value)

            touch(x, y)
            instrumentation.waitForIdleSync()
            assertFalse(paused.value)

            touch(x, y)
            awaitNode("Channel B")
            assertTrue(paused.value)

            applied.set(CountDownLatch(1))
            assertTrue(awaitNode("Channel B").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("B", state.value.selectedChannel?.uri)
            assertEquals(3, preparedChanges.get())
            assertFalse(paused.value)

            touch(x, y)
            awaitNode("Channel C")
            assertTrue(paused.value)

            touch(x, y)
            instrumentation.waitForIdleSync()
            assertFalse(paused.value)

            touch(x, y)
            awaitNode("Channel C")
            assertTrue(paused.value)
            applied.set(CountDownLatch(1))
            assertTrue(awaitNode("Channel C").performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertTrue(applied.get().await(10, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals("C", state.value.selectedChannel?.uri)
            assertFalse(paused.value)
            assertEquals(4, preparedChanges.get())

            touch(x, y)
            awaitNode("Channel D")
            assertTrue(paused.value)

            scenario.onActivity { state.value = state.value.copy(showChannelFilmstrip = false) }
            instrumentation.waitForIdleSync()
            assertTrue(paused.value)
            awaitNoNode("Channels")

            touch(x, y)
            instrumentation.waitForIdleSync()
            assertFalse(paused.value)
            touch(x, y)
            instrumentation.waitForIdleSync()
            assertTrue(paused.value)
            awaitNoNode("Channels")

            touch(x, y, y - bounds.height() / 3)
            awaitChannel(state, "D")
            assertFalse(paused.value)

            scenario.onActivity { state.value = state.value.copy(showChannelFilmstrip = true) }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(350)
            touch(x, y)
            awaitNode("Channels")
            assertTrue(paused.value)
        }
    }

    private fun awaitChannel(state: androidx.compose.runtime.State<MainUiState>, uri: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (state.value.selectedChannel?.uri != uri && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(uri, state.value.selectedChannel?.uri)
    }

    private fun touch(x: Float, y: Float, endY: Float = y) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val started = SystemClock.uptimeMillis()
        fun send(action: Int, eventY: Float) {
            val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, x, eventY, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue(automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        send(MotionEvent.ACTION_DOWN, y)
        if (endY != y) send(MotionEvent.ACTION_MOVE, endY)
        send(MotionEvent.ACTION_UP, endY)
    }

    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            findNode(automation.rootInActiveWindow, label)?.let { found ->
                var target = found
                while (!target.isClickable) target = target.parent ?: return found
                return target
            }
            SystemClock.sleep(50)
        }
        error("Could not find $label")
    }

    private fun hasNode(label: String): Boolean =
        findNode(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow, label) != null

    private fun awaitNoNode(label: String) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (hasNode(label) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertFalse("$label should be hidden", hasNode(label))
    }

    private fun findNode(node: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) {
            findNode(node.getChild(index), label)?.let { return it }
        }
        return null
    }
}
