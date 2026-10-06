package com.thekidschannel.ui

import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.MainActivity
import com.thekidschannel.MainUiState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StandardLanguageSelectionTest {
    @Test fun englishOverridesItalianDefaultsAndRendersSubtitles() = checkSelection("en", true)
    @Test fun anotherPreferredLanguageCanBeSelected() = checkSelection("it", true)
    @Test fun disablingSubtitlesAlsoDisablesTheForcedTrack() = checkSelection("en", false)
    @Test fun missingAudioLanguageFallsBackToTheDefaultTrack() = checkSelection("fr", false)

    @Test fun fontSizeChangesPlainTextSubtitles() = checkFontSize("language-tracks.mkv")
    @Test fun fontSizeChangesStyledTextSubtitles() = checkFontSize("styled-language-tracks.mkv")

    private fun checkFontSize(asset: String) {
        var normal = 0
        var large = 0
        checkSelection("en", true, 100, asset) { normal = it }
        checkSelection("en", true, 200, asset) { large = it }
        assertTrue("Font size did not enlarge subtitles: $normal -> $large", large > normal * 1.5)
    }

    private fun checkSelection(
        language: String, subtitles: Boolean, fontSize: Int = 100,
        asset: String = "language-tracks.mkv", onPixels: (Int) -> Unit = {},
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "language-tracks.mkv")
        instrumentation.context.assets.open(asset).use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var player: ExoPlayer
            lateinit var view: PlayerView
            scenario.onActivity { activity ->
                player = ExoPlayer.Builder(activity).build()
                player.applyLanguagePreferences(MainUiState(
                    audioLanguage = language, subtitleLanguage = language, subtitlesEnabled = subtitles,
                    subtitleFontSize = fontSize,
                ))
                view = PlayerView(activity).apply {
                    setBackgroundColor(Color.BLACK)
                    useController = false
                    this.player = player
                    subtitleView?.applyFontSize(fontSize)
                }
                activity.setContentView(view)
                player.volume = 0f
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                player.prepare()
                player.play()
            }
            try {
                val until = android.os.SystemClock.uptimeMillis() + 10_000
                var matched = false
                while (!matched && android.os.SystemClock.uptimeMillis() < until) {
                    scenario.onActivity {
                        val selected = player.currentTracks.groups.flatMap { group ->
                            (0 until group.length).filter { group.isTrackSelected(it) }
                                .map { group.type to group.getTrackFormat(it).language }
                        }
                        val expected = if (language == "en") listOf("en", "eng") else listOf("it", "ita")
                        matched = selected.any { it.first == C.TRACK_TYPE_AUDIO && it.second in expected } &&
                            (if (subtitles) selected.any { it.first == C.TRACK_TYPE_TEXT && it.second in expected }
                            else selected.none { it.first == C.TRACK_TYPE_TEXT }) && player.currentPosition > 1_000
                    }
                    if (!matched) Thread.sleep(50)
                }
                assertTrue("Preferred tracks not selected", matched)
                Thread.sleep(300)
                val bounds = Rect()
                scenario.onActivity { view.getGlobalVisibleRect(bounds) }
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                try {
                    var whitePixels = 0
                    for (y in bounds.top + bounds.height() / 2 until bounds.bottom - 30) {
                        for (x in bounds.left + 30 until bounds.right - 30) {
                            if (Color.red(screenshot.getPixel(x, y)) > 180) whitePixels++
                        }
                    }
                    onPixels(whitePixels)
                    assertEquals("Subtitle visibility does not match its toggle ($whitePixels white pixels)", subtitles, whitePixels > 20)
                } finally { screenshot.recycle() }
            } finally {
                scenario.onActivity { view.player = null; player.release() }
            }
        }
        file.delete()
    }
}
