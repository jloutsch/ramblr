package com.trevornk.ramblr

/**
 * Where the user parked the floating ring, independent of screen size: the corner it sits in
 * (left/right edge, top/bottom half) and its gap from that top or bottom edge. A ring dropped in
 * the bottom-left corner stays exactly that far above the bottom on every screen.
 *
 * The ring's window x/y are derived from this on every screen-size change (rotation, fold/unfold,
 * ring resize) and at overlay creation, and the anchor itself is only ever written when a drag
 * settles. Deriving pixels from a fixed anchor -- rather than re-deriving the next pixel position
 * from the last one -- is what keeps the ring from wandering: the old pixel-carrying approach left
 * a right-edge portrait ring stranded mid-screen (and often below the bottom) after a rotation, so
 * auto-peek then judged it "left of centre", and rotating back re-snapped it to the LEFT edge with
 * its height clamped to the bottom. Each round trip through clamps and proportional rescaling moved
 * it a little further from where the user's thumb wanted it.
 */
data class RingAnchor(val rightEdge: Boolean, val bottomAnchored: Boolean, val edgeGapPx: Int) {
    companion object {
        /** The anchor for a docked ring window at [x]/[y] on a [screenW] x [screenH] screen. Uses
         *  the same left/right split as the drag-release snap ([RingPeek.isSnappedToRightEdge]). */
        fun fromPosition(x: Int, y: Int, screenW: Int, screenH: Int, ringSize: Int): RingAnchor {
            val bottom = y + ringSize / 2 > screenH / 2
            val gap = if (bottom) screenH - ringSize - y else y
            return RingAnchor(RingPeek.isSnappedToRightEdge(x, screenW, ringSize), bottom, gap.coerceAtLeast(0))
        }
    }
}

/**
 * The docked (fully visible) window x/y for [anchor] on a [screenW] x [screenH] screen: flush
 * against the anchored side, [RingAnchor.edgeGapPx] from the anchored top or bottom edge. The gap
 * is capped at half the free height so a ring parked mid-screen on a tall screen lands mid-screen,
 * not on the opposite edge, of a short one; and the result is clamped so the whole ring stays
 * on-screen (FLAG_LAYOUT_NO_LIMITS disables WindowManager's own clamping). A null anchor (fresh
 * install, never dragged) is the right edge, vertically centred.
 */
fun dockedPositionFor(anchor: RingAnchor?, screenW: Int, screenH: Int, ringSize: Int, margin: Int): Pair<Int, Int> {
    val maxX = (screenW - ringSize - margin).coerceAtLeast(margin)
    val maxY = (screenH - ringSize - margin).coerceAtLeast(margin)
    if (anchor == null) return maxX to (screenH / 2 - ringSize / 2).coerceIn(margin, maxY)
    val x = if (anchor.rightEdge) maxX else margin
    val gap = anchor.edgeGapPx.coerceAtMost((screenH - ringSize) / 2)
    val y = if (anchor.bottomAnchored) screenH - ringSize - gap else gap
    return x to y.coerceIn(margin, maxY)
}

/** Anchors are kept per orientation, so a thumb spot chosen in portrait survives a drag made
 *  while the phone was sideways. */
fun isLandscape(screenW: Int, screenH: Int): Boolean = screenW > screenH
