package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #128: a rotation while the ring is PEEKED must re-derive the peeked x against the new screen,
 * not leave it at the old absolute x -- floating mid-screen (portrait -> landscape) or entirely off
 * it (landscape -> portrait). The service peeks from the anchor's docked x for the new screen
 * ([dockedPositionFor] then [RingPeek.peekedX]); these pin that combination.
 */
class PeekRotationTest {

    private val ringSize = 154
    private val margin = 22
    private val peekVisiblePx = 55
    private val inset = 16

    private fun peekedX(anchor: RingAnchor, screenW: Int, screenH: Int): Int {
        val (dockedX, _) = dockedPositionFor(anchor, screenW, screenH, ringSize, margin)
        return RingPeek.peekedX(dockedX, screenW, ringSize, peekVisiblePx, inset)
    }

    @Test fun `peeked right-edge ring rotated to landscape peeks against the new right edge`() {
        val x = peekedX(RingAnchor(true, true, 200), 2400, 1080)
        assertEquals(2400 - peekVisiblePx - inset, x)
    }

    @Test fun `peeked right-edge ring rotated back to portrait does not fall off the screen`() {
        val x = peekedX(RingAnchor(true, true, 200), 1080, 2400)
        assertTrue("peeked x must be within the new screen", x < 1080)
        assertEquals(1080 - peekVisiblePx - inset, x)
    }

    @Test fun `a left-edge peeked ring survives rotation too`() {
        assertEquals(peekVisiblePx - ringSize + inset, peekedX(RingAnchor(false, false, 600), 2400, 1080))
        assertEquals(peekVisiblePx - ringSize + inset, peekedX(RingAnchor(false, false, 600), 1080, 2400))
    }
}
