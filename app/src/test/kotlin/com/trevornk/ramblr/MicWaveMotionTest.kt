package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicWaveMotionTest {
    @Test fun `the cap is about thirty frames a second`() {
        assertTrue(MicWaveMotion.FRAME_INTERVAL_MS in 30L..40L)
    }

    @Test fun `the first frame is due at once`() {
        assertEquals(0L, MicWaveMotion.delayUntilNextFrame(nowMs = 5_000, lastFrameMs = 0))
    }

    @Test fun `a frame drawn just now waits out the rest of the interval`() {
        assertEquals(33L, MicWaveMotion.delayUntilNextFrame(nowMs = 1_000, lastFrameMs = 1_000))
        assertEquals(23L, MicWaveMotion.delayUntilNextFrame(nowMs = 1_010, lastFrameMs = 1_000))
    }

    @Test fun `a late frame is not delayed further and the delay never exceeds the interval`() {
        assertEquals(0L, MicWaveMotion.delayUntilNextFrame(nowMs = 1_100, lastFrameMs = 1_000))
        // a clock that went backwards must not stall the wave
        assertEquals(33L, MicWaveMotion.delayUntilNextFrame(nowMs = 500, lastFrameMs = 1_000))
    }

    @Test fun `simulated 120 Hz vsync draws at most one frame per interval`() {
        var last = 0L
        var frames = 0
        var now = 1L
        while (now < 1_001L) {
            if (MicWaveMotion.delayUntilNextFrame(now, last) == 0L) { frames++; last = now }
            now += 8 // ~120 Hz display ticks
        }
        assertTrue("drew $frames frames in a second", frames <= 31)
        assertTrue(frames >= 20)
    }

    @Test fun `animator scale zero is reduced motion, anything else animates`() {
        assertTrue(MicWaveMotion.reducedMotion(0f))
        assertFalse(MicWaveMotion.reducedMotion(1f))
        assertFalse(MicWaveMotion.reducedMotion(0.5f))
        assertFalse(MicWaveMotion.reducedMotion(10f))
    }
}
