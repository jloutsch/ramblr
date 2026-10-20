package com.trevornk.ramblr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Frame pacing and reduced-motion rules for [MicWaveView], kept free of Android so they can be
 * unit-tested. (The view itself needs a Canvas.)
 */
internal object MicWaveMotion {
    /** The wave is decorative feedback, not video: ~30 frames a second is plenty and halves the
     *  work a 60/120 Hz display would otherwise ask of the keyboard process. */
    const val FRAME_INTERVAL_MS = 33L

    /** Milliseconds to wait before drawing again so frames land at least [intervalMs] apart.
     *  [lastFrameMs] is 0 before the first frame, which is due immediately. */
    fun delayUntilNextFrame(nowMs: Long, lastFrameMs: Long, intervalMs: Long = FRAME_INTERVAL_MS): Long {
        if (lastFrameMs <= 0L) return 0L
        return (lastFrameMs + intervalMs - nowMs).coerceIn(0L, intervalMs)
    }

    /** The system "remove animations" switch (developer option / accessibility setting) writes an
     *  animator duration scale of 0. Anything else, including an unreadable value, animates. */
    fun reducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    fun reducedMotion(context: Context): Boolean = runCatching {
        reducedMotion(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }.getOrDefault(false)
}

/**
 * Sound waves drawn inside the keyboard's mic button while recording, in place of the mic glyph.
 * [push] takes the runtime's 0..1 loudness about 20 times a second.
 *
 * A few overlapping sine waves whose height follows the voice; in silence they settle into a flat
 * line ([push] gets 0 for room noise, see [AudioLevelMeter]). Redraws are paced to
 * [MicWaveMotion.FRAME_INTERVAL_MS], and with the system's animations turned off the view draws the
 * flat line once and never schedules another frame.
 */
internal class MicWaveView(context: Context) : View(context) {
    /** Wave colour; the button behind it supplies the contrast. */
    var color: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private var active = false
    private var target = 0f
    private var shown = 0f
    private var lastFrameMs = 0L
    private var reducedMotion = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }

    /** Shows the waves while recording; turning them off forgets the level. */
    fun setActive(on: Boolean) {
        if (active == on) return
        active = on
        target = 0f
        shown = 0f
        lastFrameMs = 0L
        reducedMotion = on && MicWaveMotion.reducedMotion(context)
        if (on) scheduleFrame() else invalidate()
    }

    fun push(level: Float) {
        target = level.coerceIn(0f, 1f)
        // No invalidate here: the frame loop started by [setActive] picks the level up on its next
        // tick, so the 20 Hz level stream cannot raise the redraw rate above the cap.
    }

    internal fun targetForTest(): Float = target
    internal fun reducedMotionForTest(): Boolean = reducedMotion

    private fun scheduleFrame() {
        postInvalidateDelayed(MicWaveMotion.delayUntilNextFrame(SystemClock.uptimeMillis(), lastFrameMs))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!active) return
        if (reducedMotion) {
            drawSine(canvas, level = 0f, seconds = 0f)
            return
        }
        val now = SystemClock.uptimeMillis()
        shown = ease(shown, target, frameDelta(now))
        drawSine(canvas, level = shown, seconds = now / 1000f)
        scheduleFrame()
    }

    /** Milliseconds since the previous frame, so easing runs at the same speed at any frame rate. */
    private fun frameDelta(now: Long): Float {
        val dt = if (lastFrameMs == 0L) 16L else (now - lastFrameMs).coerceIn(0L, 100L)
        lastFrameMs = now
        return dt.toFloat()
    }

    private fun ease(from: Float, to: Float, dt: Float): Float =
        from + (to - from) * (1f - exp(-dt / (if (to > from) RISE_MS else FALL_MS)))

    private fun drawSine(canvas: Canvas, level: Float, seconds: Float) {
        val size = min(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val half = size * 0.3f
        clip.reset()
        clip.addCircle(cx, cy, size / 2f, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clip)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val amplitude = size * SINE_MAX_AMPLITUDE * lift(level)
        for ((index, wave) in WAVES.withIndex()) {
            path.reset()
            val steps = 48
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                // Pinned to the centre line at both ends so the waves read as one bundle.
                val taper = sin(PI * t).toFloat()
                val y = cy + amplitude * wave.scale * taper *
                    sin(2 * PI * (wave.cycles * t + wave.speed * seconds)).toFloat()
                val x = cx - half + 2 * half * t
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.color = color
            paint.alpha = wave.alpha
            paint.strokeWidth = (if (index == 0) 2.25f else 1.5f) * resources.displayMetrics.density
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    /** Lifts quiet-to-normal speech so the waves use most of their height without clipping
     *  loud speech. */
    private fun lift(level: Float): Float = level.coerceIn(0f, 1f).pow(LIFT)

    private class Wave(val cycles: Float, val speed: Float, val scale: Float, val alpha: Int)

    private companion object {
        /** Peak sine height either side of the centre line, as a share of the button. */
        const val SINE_MAX_AMPLITUDE = 0.24f
        const val LIFT = 0.7f
        const val RISE_MS = 110f
        const val FALL_MS = 280f
        val WAVES = listOf(
            Wave(cycles = 1.5f, speed = 0.6f, scale = 1f, alpha = 0xFF),
            Wave(cycles = 2.2f, speed = -0.85f, scale = 0.7f, alpha = 0x99),
            Wave(cycles = 1.1f, speed = 0.4f, scale = 0.5f, alpha = 0x66),
        )
    }
}
