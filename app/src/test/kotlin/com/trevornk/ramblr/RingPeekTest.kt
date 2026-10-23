package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RingPeekTest {

    private val screenW = 1000
    private val ringSize = 100

    @Test fun `snapped to right edge when x is past screen midpoint`() {
        assertTrue(RingPeek.isSnappedToRightEdge(x = 900, screenW, ringSize))
    }

    @Test fun `snapped to left edge when x is before screen midpoint`() {
        assertFalse(RingPeek.isSnappedToRightEdge(x = 0, screenW, ringSize))
    }

    @Test fun `peeked x on the right edge leaves only the sliver on-screen`() {
        val peekVisiblePx = 20
        val x = RingPeek.peekedX(x = 900, screenW, ringSize, peekVisiblePx)
        assertEquals(screenW - peekVisiblePx, x)
    }

    @Test fun `peeked x on the left edge leaves only the sliver on-screen`() {
        val peekVisiblePx = 20
        val x = RingPeek.peekedX(x = 0, screenW, ringSize, peekVisiblePx)
        assertEquals(peekVisiblePx - ringSize, x)
    }

    @Test fun `no peek against the edge the navigation bar is on`() {
        assertFalse(RingPeek.canPeek(x = 900, screenW, ringSize, navLeftPx = 0, navRightPx = 126))
        assertFalse(RingPeek.canPeek(x = 0, screenW, ringSize, navLeftPx = 126, navRightPx = 0))
    }

    @Test fun `peek against the edge opposite the navigation bar, or with no side bar`() {
        assertTrue(RingPeek.canPeek(x = 0, screenW, ringSize, navLeftPx = 0, navRightPx = 126))
        assertTrue(RingPeek.canPeek(x = 900, screenW, ringSize, navLeftPx = 126, navRightPx = 0))
        assertTrue(RingPeek.canPeek(x = 900, screenW, ringSize, navLeftPx = 0, navRightPx = 0))
    }

    @Test fun `a left peek goes past a left display cutout to the real edge`() {
        // S23+ with the camera on the left: x=0 starts after the 94 px cutout, so the display's
        // real left edge is at -94 and the 52 px sliver plus 15 px inset ends at x=-27 (#352).
        assertEquals(-94 + 52 - 147 + 15, RingPeek.peekedX(x = 21, screenW = 2120, ringSize = 147, peekVisiblePx = 52, ringInsetPx = 15, leftEdge = -94))
    }

    @Test fun `auto-peek allowed only while idle`() {
        assertTrue(RingPeek.shouldAutoPeek(RecordingStateMachine.State.IDLE))
        assertFalse(RingPeek.shouldAutoPeek(RecordingStateMachine.State.RECORDING))
        assertFalse(RingPeek.shouldAutoPeek(RecordingStateMachine.State.TRANSCRIBING))
    }
}
