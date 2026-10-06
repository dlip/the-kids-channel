package com.thekidschannel.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFrameGateTest {
    @Test
    fun ignoresPausedFramesAndFramesBeforeTheResumePosition() {
        val gate = PlaybackFrameGate(20_000)
        assertFalse(gate.onFrame(20_000, false))
        assertFalse(gate.onFrame(0, true))
        assertFalse(gate.onFrame(19_000, true))
        assertFalse(gate.onFrame(20_000, true))
        assertTrue(gate.onFrame(20_000, true))
    }

    @Test
    fun resumesWithoutWaitingForPlaybackPositionUpdates() {
        val gate = PlaybackFrameGate(20_000)
        assertFalse(gate.onFrame(20_000, true))
        assertTrue(gate.onFrame(20_000, true))
        assertTrue(gate.onFrame(20_000, false))
    }

    @Test
    fun aNewActivationCannotReuseReadinessFromThePreviousOne() {
        val previous = PlaybackFrameGate(0)
        assertFalse(previous.onFrame(100, true))
        assertTrue(previous.onFrame(200, true))
        val resumed = PlaybackFrameGate(200)
        assertFalse(resumed.onFrame(200, true))
        assertTrue(resumed.onFrame(200, true))
    }

    @Test
    fun changingVideosClearsReadinessAndUsesTheNewResumePosition() {
        val gate = PlaybackFrameGate(0)
        assertFalse(gate.onFrame(100, true))
        assertTrue(gate.onFrame(200, true))
        gate.reset(20_000)
        assertFalse(gate.onFrame(200, true))
        assertFalse(gate.onFrame(20_000, false))
        assertFalse(gate.onFrame(20_000, true))
        assertTrue(gate.onFrame(20_000, true))
    }
}
