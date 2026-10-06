package com.thekidschannel.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ViewGroup
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.thekidschannel.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StandardSubtitleAppearanceTest {
    @Test fun embeddedBlackBoxesDoNotCoverThePicture() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: SubtitleView
            scenario.onActivity { activity ->
                view = SubtitleView(activity).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setBackgroundColor(Color.rgb(220, 120, 50))
                    applySubtitleAppearance(100)
                }
                activity.setContentView(view)
                view.setCues(listOf(Cue.Builder().setText("Subtitle background").build()))
            }
            val plain = screenshot()
            try {
                scenario.onActivity {
                    val text = SpannableString("Subtitle background").apply {
                        setSpan(BackgroundColorSpan(Color.BLACK), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    view.setCues(listOf(Cue.Builder().setText(text).setWindowColor(Color.BLACK).build()))
                }
                val styled = screenshot()
                try {
                    var plainBlack = 0
                    var styledBlack = 0
                    for (y in plain.height / 2 until plain.height) {
                        for (x in 0 until plain.width) {
                            fun black(color: Int) = Color.red(color) < 40 && Color.green(color) < 40 && Color.blue(color) < 40
                            if (black(plain.getPixel(x, y))) plainBlack++
                            if (black(styled.getPixel(x, y))) styledBlack++
                        }
                    }
                    assertTrue("Embedded backgrounds added black pixels: $plainBlack -> $styledBlack", styledBlack <= plainBlack * 1.1 + 20)
                    var textPixels = 0
                    for (y in plain.height / 2 until plain.height) {
                        for (x in 0 until plain.width) {
                            val color = plain.getPixel(x, y)
                            if (Color.red(color) > 240 && Color.green(color) > 240 && Color.blue(color) > 240) textPixels++
                        }
                    }
                    assertTrue("Subtitles were hidden rather than styled", textPixels > 100)
                } finally { styled.recycle() }
            } finally { plain.recycle() }
        }
    }

    private fun screenshot(): Bitmap {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(200)
        return InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
    }
}
