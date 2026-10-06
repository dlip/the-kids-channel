package com.thekidschannel.ui

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thekidschannel.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class VideoFrameCaptureTest {
    @Test
    fun capturesTheLatestPausedTextureWithoutReplacingItsSurface() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val available = CountDownLatch(1)
            lateinit var texture: TextureView
            scenario.onActivity { activity ->
                texture = TextureView(activity).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            available.countDown()
                        }
                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                    }
                }
                (activity.window.decorView as ViewGroup).addView(texture, ViewGroup.LayoutParams(320, 180))
            }
            assertTrue("Texture surface was not created", available.await(10, TimeUnit.SECONDS))
            val original = texture.surfaceTexture
            val surface = Surface(original)
            try {
                for (color in listOf(Color.RED, Color.BLUE)) {
                    val canvas = surface.lockCanvas(null)
                    canvas.drawColor(color)
                    surface.unlockCanvasAndPost(canvas)
                    val bitmap = runBlocking { withTimeout(5_000) { captureVideoFrame(texture) } }
                    assertNotNull("Could not capture the paused texture", bitmap)
                    try {
                        assertEquals(color, bitmap!!.getPixel(bitmap.width / 2, bitmap.height / 2))
                        assertTrue("Capture replaced the video surface", original === texture.surfaceTexture)
                    } finally {
                        bitmap?.recycle()
                    }
                }
            } finally {
                surface.release()
                scenario.onActivity { (texture.parent as? ViewGroup)?.removeView(texture) }
            }
        }
    }
}
