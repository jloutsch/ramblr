package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRepositionTest {

    private val ringSize = 154
    private val margin = 22

    // Phone in portrait / landscape (a plain W/H swap), and a Pixel Fold's cover and inner screens.
    private val portraitW = 1080
    private val portraitH = 2400
    private val landscapeW = 2400
    private val landscapeH = 1080
    private val innerW = 2076
    private val innerH = 2152

    // --- RingAnchor.fromPosition ---

    @Test fun `a ring parked on the right edge anchors right`() {
        val anchor = RingAnchor.fromPosition(portraitW - ringSize - margin, 1800, portraitW, portraitH, ringSize)
        assertTrue(anchor.rightEdge)
        assertEquals((1800 + ringSize / 2).toFloat() / portraitH, anchor.centerYFraction, 0.0001f)
    }

    @Test fun `a ring parked on the left edge anchors left`() {
        assertFalse(RingAnchor.fromPosition(margin, 600, portraitW, portraitH, ringSize).rightEdge)
    }

    @Test fun `the anchored fraction is clamped to the screen`() {
        assertEquals(1f, RingAnchor.fromPosition(margin, 5000, portraitW, portraitH, ringSize).centerYFraction, 0f)
        assertEquals(0f, RingAnchor.fromPosition(margin, -500, portraitW, portraitH, ringSize).centerYFraction, 0f)
    }

    // --- dockedPositionFor ---

    @Test fun `a drag-settled position round-trips through its anchor exactly`() {
        val x = portraitW - ringSize - margin
        val y = 1733
        val anchor = RingAnchor.fromPosition(x, y, portraitW, portraitH, ringSize)
        assertEquals(x to y, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
    }

    @Test fun `the default anchor is the right edge, vertically centred`() {
        val (x, y) = dockedPositionFor(RingAnchor.DEFAULT, portraitW, portraitH, ringSize, margin)
        assertEquals(portraitW - ringSize - margin, x)
        assertEquals(portraitH / 2 - ringSize / 2, y)
    }

    @Test fun `a right-edge ring hugs the right edge of a wider screen`() {
        val (x, _) = dockedPositionFor(RingAnchor(true, 0.5f), innerW, innerH, ringSize, margin)
        assertEquals(innerW - ringSize - margin, x)
    }

    @Test fun `a left-edge ring stays on the left edge`() {
        val (x, _) = dockedPositionFor(RingAnchor(false, 0.5f), landscapeW, landscapeH, ringSize, margin)
        assertEquals(margin, x)
    }

    @Test fun `a ring anchored near the bottom stays fully on-screen`() {
        val (_, y) = dockedPositionFor(RingAnchor(true, 1f), landscapeW, landscapeH, ringSize, margin)
        assertEquals(landscapeH - ringSize - margin, y)
    }

    @Test fun `a ring anchored at the top stays fully on-screen`() {
        val (_, y) = dockedPositionFor(RingAnchor(true, 0f), portraitW, portraitH, ringSize, margin)
        assertEquals(margin, y)
    }

    // --- the wandering-ring bug ---

    @Test fun `rotating to landscape and back returns a low thumb-spot ring to exactly where it was`() {
        // Right edge, low down where a right thumb reaches.
        val start = portraitW - ringSize - margin to 1900
        val anchor = RingAnchor.fromPosition(start.first, start.second, portraitW, portraitH, ringSize)

        val (lx, ly) = dockedPositionFor(anchor, landscapeW, landscapeH, ringSize, margin)
        // In landscape it hugs the right edge and is on-screen -- previously it kept x=904 (mid
        // screen, so auto-peek slid it LEFT) and y=1900 (below the 1080px-tall screen).
        assertEquals(landscapeW - ringSize - margin, lx)
        assertTrue(ly + ringSize <= landscapeH)
        assertTrue(RingPeek.isSnappedToRightEdge(lx, landscapeW, ringSize))

        repeat(5) { dockedPositionFor(anchor, landscapeW, landscapeH, ringSize, margin) }
        assertEquals(start, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
    }

    @Test fun `a fold and unfold returns the ring to exactly where it was`() {
        val start = portraitW - ringSize - margin to 1500
        val anchor = RingAnchor.fromPosition(start.first, start.second, portraitW, portraitH, ringSize)
        val (ix, _) = dockedPositionFor(anchor, innerW, innerH, ringSize, margin)
        assertEquals(innerW - ringSize - margin, ix)
        assertEquals(start, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
    }

    @Test fun `landscape is wider than tall`() {
        assertTrue(isLandscape(landscapeW, landscapeH))
        assertFalse(isLandscape(portraitW, portraitH))
        assertTrue(isLandscape(innerW + 200, innerH))
        assertFalse(isLandscape(innerW, innerH))
    }
}
