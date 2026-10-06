package com.thekidschannel.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal suspend fun captureVideoFrame(container: View): Bitmap? =
    withContext(Dispatchers.Main.immediate) {
        val source = container.videoOutputs()
            .filter { it.width > 0 && it.height > 0 }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: return@withContext null
        val scale = minOf(1f, MAX_PREVIEW_WIDTH.toFloat() / source.width)
        val bitmap = Bitmap.createBitmap(
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )

        when (source) {
            is TextureView -> {
                val surface = Surface(source.surfaceTexture)
                captureSurface(surface, bitmap) { surface.release() }
            }
            is SurfaceView -> captureSurface(source.holder.surface, bitmap)
            else -> {
                bitmap.recycle()
                null
            }
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun captureSurface(
    source: Surface,
    bitmap: Bitmap,
    releaseSource: () -> Unit = {},
): Bitmap? {
    if (!source.isValid) {
        releaseSource()
        bitmap.recycle()
        return null
    }
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                source,
                bitmap,
                { result ->
                    releaseSource()
                    if (result == PixelCopy.SUCCESS && continuation.isActive) {
                        continuation.resume(bitmap) { bitmap.recycle() }
                    } else {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            releaseSource()
            bitmap.recycle()
            continuation.resume(null)
        }
    }
}

private fun View.videoOutputs(): List<View> = buildList {
    when (this@videoOutputs) {
        is SurfaceView -> add(this@videoOutputs)
        is TextureView -> if (isAvailable) add(this@videoOutputs)
        is ViewGroup -> repeat(childCount) { childIndex ->
            addAll(getChildAt(childIndex).videoOutputs())
        }
    }
}

private const val MAX_PREVIEW_WIDTH = 640
