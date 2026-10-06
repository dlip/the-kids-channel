package com.thekidschannel.media

internal class PlaybackFrameGate(private var startPositionMs: Long) {
    private var frames = 0

    fun reset(positionMs: Long) {
        startPositionMs = positionMs
        frames = 0
    }

    fun onFrame(positionMs: Long, playing: Boolean): Boolean {
        if (frames >= 2) return true
        if (!playing || positionMs < startPositionMs) return false
        frames += 1
        return frames >= 2
    }
}
