package com.trevornk.ramblr

import kotlin.math.roundToInt

/**
 * Where the user parked the floating ring, independent of screen size: which edge it hugs and how
 * far down the screen its centre sits (0 = top, 1 = bottom).
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
data class RingAnchor(val rightEdge: Boolean, val centerYFraction: Float) {
    companion object {
        /** First run, before any drag: right edge, vertically centred. */
        val DEFAULT = RingAnchor(rightEdge = true, centerYFraction = 0.5f)

        /** The anchor for a docked ring window at [x]/[y] on a [screenW] x [screenH] screen. Uses
         *  the same left/right split as the drag-release snap ([RingPeek.isSnappedToRightEdge]). */
        fun fromPosition(x: Int, y: Int, screenW: Int, screenH: Int, ringSize: Int): RingAnchor {
            val fraction = if (screenH <= 0) 0.5f else (y + ringSize / 2).toFloat() / screenH
            return RingAnchor(RingPeek.isSnappedToRightEdge(x, screenW, ringSize), fraction.coerceIn(0f, 1f))
        }
    }
}

/**
 * The docked (fully visible) window x/y for [anchor] on a [screenW] x [screenH] screen: flush
 * against the anchored edge, centre at the anchored fraction of the height, clamped so the whole
 * ring stays on-screen (FLAG_LAYOUT_NO_LIMITS disables WindowManager's own clamping).
 */
fun dockedPositionFor(anchor: RingAnchor, screenW: Int, screenH: Int, ringSize: Int, margin: Int): Pair<Int, Int> {
    val x = if (anchor.rightEdge) (screenW - ringSize - margin).coerceAtLeast(margin) else margin
    val maxY = (screenH - ringSize - margin).coerceAtLeast(margin)
    val y = ((anchor.centerYFraction * screenH).roundToInt() - ringSize / 2).coerceIn(margin, maxY)
    return x to y
}

/** Anchors are kept per orientation, so a thumb spot chosen in portrait survives a drag made
 *  while the phone was sideways. */
fun isLandscape(screenW: Int, screenH: Int): Boolean = screenW > screenH
