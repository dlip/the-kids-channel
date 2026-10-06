package com.thekidschannel.ui

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.MainActivity
import com.thekidschannel.MainUiState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

class VlcLanguageSelectionTest {
    @Test fun englishOverridesItalianDefaultsAndRendersSubtitles() = checkSelection("en", true)
    @Test fun anotherPreferredLanguageCanBeSelected() = checkSelection("it", true)
    @Test fun disablingSubtitlesAlsoDisablesTheForcedTrack() = checkSelection("en", false)
    @Test fun missingAudioLanguageFallsBackToTheDefaultTrack() = checkSelection("fr", false)

    @Test fun fontSizeChangesPlainTextSubtitles() = checkFontSize("language-tracks.mkv")
    @Test fun styledSubtitlesKeepTheirEmbeddedFontSize() {
        var normal = 0
        var large = 0
        checkSelection("en", true, 100, "styled-language-tracks.mkv") { normal = it }
        checkSelection("en", true, 200, "styled-language-tracks.mkv") { large = it }
        assertTrue("Styled subtitle size unexpectedly changed: $normal -> $large",
            kotlin.math.abs(large - normal) <= normal * 0.1)
    }

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
            lateinit var engine: LibVLC
            lateinit var player: MediaPlayer
            lateinit var layout: VLCVideoLayout
            scenario.onActivity { activity ->
                engine = LibVLC(activity, vlcLanguageOptions(MainUiState(
                    audioLanguage = language, subtitleLanguage = language, subtitlesEnabled = subtitles,
                    subtitleFontSize = fontSize,
                )))
                player = MediaPlayer(engine)
                layout = VLCVideoLayout(activity)
                activity.setContentView(layout)
                player.attachViews(layout, null, false, true)
                val media = Media(engine, Uri.fromFile(file))
                player.media = media
                media.release()
                player.volume = 0
                player.play()
            }
            try {
                val until = android.os.SystemClock.uptimeMillis() + 10_000
                var matched = false
                while (!matched && android.os.SystemClock.uptimeMillis() < until) {
                    scenario.onActivity {
                        val media = player.media
                        if (media != null) {
                            val tracks = (0 until media.trackCount).map { media.getTrack(it) }
                            val expected = if (language == "en") "eng" else "ita"
                            val audio = tracks.firstOrNull { it.type == 0 && it.language == expected }
                            val text = tracks.firstOrNull { it.type == 2 && it.language == expected }
                            matched = audio != null && player.audioTrack == audio.id &&
                                (if (subtitles) text != null && player.spuTrack == text.id else player.spuTrack == -1)
                            media.release()
                        }
                    }
                    if (!matched) Thread.sleep(50)
                }
                assertTrue("Preferred tracks not selected: audio=${player.audioTrack}, subtitles=${player.spuTrack}", matched)
                Thread.sleep(1_000)
                val bitmap = runBlocking { withTimeout(5_000) { captureVideoFrame(layout) } }
                assertNotNull(bitmap)
                try {
                    val pixels = IntArray(bitmap!!.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val whitePixels = pixels.count { android.graphics.Color.red(it) > 180 }
                    onPixels(whitePixels)
                    assertEquals("Subtitle visibility does not match its toggle ($whitePixels white pixels)", subtitles, whitePixels > 20)
                } finally { bitmap?.recycle() }
            } finally {
                scenario.onActivity { player.stop(); player.detachViews(); player.release(); engine.release() }
            }
        }
        file.delete()
    }
}
