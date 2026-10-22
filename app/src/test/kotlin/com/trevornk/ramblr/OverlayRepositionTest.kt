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

    @Test fun `a ring parked low on the left anchors to the bottom-left corner`() {
        val y = portraitH - ringSize - 120
        val anchor = RingAnchor.fromPosition(margin, y, portraitW, portraitH, ringSize)
        assertEquals(RingAnchor(rightEdge = false, bottomAnchored = true, edgeGapPx = 120), anchor)
    }

    @Test fun `a ring parked high on the right anchors to the top-right corner`() {
        val anchor = RingAnchor.fromPosition(portraitW - ringSize - margin, 300, portraitW, portraitH, ringSize)
        assertEquals(RingAnchor(rightEdge = true, bottomAnchored = false, edgeGapPx = 300), anchor)
    }

    @Test fun `a position past the screen edge anchors with no gap`() {
        assertEquals(0, RingAnchor.fromPosition(margin, 5000, portraitW, portraitH, ringSize).edgeGapPx)
        assertEquals(0, RingAnchor.fromPosition(margin, -500, portraitW, portraitH, ringSize).edgeGapPx)
    }

    // --- dockedPositionFor ---

    @Test fun `a drag-settled position round-trips through its anchor exactly`() {
        for (y in listOf(margin, 600, 1733, portraitH - ringSize - margin)) {
            val x = portraitW - ringSize - margin
            val anchor = RingAnchor.fromPosition(x, y, portraitW, portraitH, ringSize)
            assertEquals(x to y, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
        }
    }

    @Test fun `no anchor yet is the right edge, vertically centred`() {
        val (x, y) = dockedPositionFor(null, portraitW, portraitH, ringSize, margin)
        assertEquals(portraitW - ringSize - margin, x)
        assertEquals(portraitH / 2 - ringSize / 2, y)
    }

    @Test fun `a right-edge ring hugs the right edge of a wider screen`() {
        val (x, _) = dockedPositionFor(RingAnchor(true, true, 100), innerW, innerH, ringSize, margin)
        assertEquals(innerW - ringSize - margin, x)
    }

    @Test fun `a gap smaller than the margin is clamped onto the screen`() {
        val (_, y) = dockedPositionFor(RingAnchor(true, true, 0), landscapeW, landscapeH, ringSize, margin)
        assertEquals(landscapeH - ringSize - margin, y)
        val (_, top) = dockedPositionFor(RingAnchor(true, false, 0), landscapeW, landscapeH, ringSize, margin)
        assertEquals(margin, top)
    }

    @Test fun `a mid-screen ring on a tall screen stays mid-screen on a short one`() {
        // Top-anchored 1000px down a 2400px portrait screen: in 1080px landscape it must not be
        // shoved down onto the bottom edge.
        val (_, y) = dockedPositionFor(RingAnchor(true, false, 1000), landscapeW, landscapeH, ringSize, margin)
        assertEquals((landscapeH - ringSize) / 2, y)
    }

    // --- the wandering-ring bug ---

    @Test fun `a bottom-left ring stays the same distance above the bottom in every orientation`() {
        val gap = 90
        val start = margin to portraitH - ringSize - gap
        val anchor = RingAnchor.fromPosition(start.first, start.second, portraitW, portraitH, ringSize)

        assertEquals(margin to landscapeH - ringSize - gap, dockedPositionFor(anchor, landscapeW, landscapeH, ringSize, margin))
        assertEquals(margin to innerH - ringSize - gap, dockedPositionFor(anchor, innerW, innerH, ringSize, margin))
        assertEquals(start, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
    }

    @Test fun `rotating to landscape and back returns a right-thumb ring to exactly where it was`() {
        val start = portraitW - ringSize - margin to 1900
        val anchor = RingAnchor.fromPosition(start.first, start.second, portraitW, portraitH, ringSize)

        val (lx, ly) = dockedPositionFor(anchor, landscapeW, landscapeH, ringSize, margin)
        // In landscape it hugs the right edge and is on-screen -- previously it kept x=904 (mid
        // screen, so auto-peek slid it LEFT) and y=1900 (below the 1080px-tall screen).
        assertEquals(landscapeW - ringSize - margin, lx)
        assertTrue(ly + ringSize <= landscapeH)
        assertTrue(RingPeek.isSnappedToRightEdge(lx, landscapeW, ringSize))

        assertEquals(start, dockedPositionFor(anchor, portraitW, portraitH, ringSize, margin))
    }

    @Test fun `landscape is wider than tall`() {
        assertTrue(isLandscape(landscapeW, landscapeH))
        assertFalse(isLandscape(portraitW, portraitH))
        assertTrue(isLandscape(innerW + 200, innerH))
        assertFalse(isLandscape(innerW, innerH))
    }
}
