package com.trevornk.ramblr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Debug-only scoring for the mic probe ([MicProbeActivity]). Pure Kotlin so it runs on the JVM.
 *
 * [hfFraction] is the share of energy above [HF_CUTOFF_HZ], where the fricatives that separate
 * "thanks" from "tanks" live; mics and audio sources attenuate that band by different amounts.
 * [snrDb] compares the 90th-percentile 20 ms frame RMS (speech) with the 10th (noise floor).
 * The metric follows Outspoke's MicScorer so results are comparable with its calibration screen.
 */
data class MicProbeScore(
    val hfFraction: Float,
    val snrDb: Float,
    val peakDbfs: Float,
    val clippedFraction: Float,
)

data class WordErrorResult(val errors: Int, val referenceWords: Int)

object MicProbeScoring {

    const val HF_CUTOFF_HZ = 3000.0
    private const val FRAME_MS = 20
    private const val CLIP_THRESHOLD = 32_700

    fun score(samples: ShortArray, sampleRate: Int): MicProbeScore {
        if (samples.size < sampleRate / 4) return MicProbeScore(0f, 0f, -96f, 0f)

        val hp = highPass(samples, sampleRate, HF_CUTOFF_HZ)
        var total = 0.0
        var hf = 0.0
        var peak = 0
        var clipped = 0
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            total += s * s
            hf += hp[i] * hp[i]
            val a = abs(samples[i].toInt())
            if (a > peak) peak = a
            if (a >= CLIP_THRESHOLD) clipped++
        }
        val hfFraction = if (total > 1e-9) (hf / total).coerceIn(0.0, 1.0).toFloat() else 0f
        val peakDbfs = if (peak == 0) -96f else (20 * log10(peak / 32767.0)).toFloat()
        return MicProbeScore(
            hfFraction = hfFraction,
            snrDb = snrDb(samples, sampleRate),
            peakDbfs = peakDbfs,
            clippedFraction = clipped.toFloat() / samples.size,
        )
    }

    /** Word-level edit distance between what was asked for and what the recognizer heard. */
    fun wordErrors(expected: String, actual: String): WordErrorResult {
        val ref = words(expected)
        val hyp = words(actual)
        var prev = IntArray(hyp.size + 1) { it }
        for (i in 1..ref.size) {
            val cur = IntArray(hyp.size + 1)
            cur[0] = i
            for (j in 1..hyp.size) {
                val sub = prev[j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            prev = cur
        }
        return WordErrorResult(prev[hyp.size], ref.size)
    }

    private fun words(text: String): List<String> =
        text.lowercase()
            .replace('’', '\'')
            .replace(Regex("[^\\p{L}\\p{N}']+"), " ")
            .split(' ')
            .filter { it.isNotBlank() }

    private fun snrDb(samples: ShortArray, sampleRate: Int): Float {
        val frame = sampleRate * FRAME_MS / 1000
        val count = samples.size / frame
        if (count < 2) return 0f
        val rms = DoubleArray(count) { f ->
            var sum = 0.0
            for (i in f * frame until (f + 1) * frame) {
                val s = samples[i].toDouble()
                sum += s * s
            }
            sqrt(sum / frame)
        }
        rms.sort()
        val noise = rms[(count * 0.1).toInt().coerceIn(0, count - 1)]
        val signal = rms[(count * 0.9).toInt().coerceIn(0, count - 1)]
        if (noise < 1e-5 || signal <= noise) return 0f
        return (20 * log10(signal / noise)).coerceAtMost(60.0).toFloat()
    }

    /** Second-order Butterworth high-pass (RBJ biquad). */
    private fun highPass(samples: ShortArray, sampleRate: Int, cutoffHz: Double): DoubleArray {
        val w0 = 2 * PI * cutoffHz / sampleRate
        val alpha = sin(w0) / (2 * 0.7071)
        val c = cos(w0)
        val a0 = 1 + alpha
        val b0 = (1 + c) / 2 / a0
        val b1 = -(1 + c) / a0
        val b2 = b0
        val a1 = -2 * c / a0
        val a2 = (1 - alpha) / a0
        val out = DoubleArray(samples.size)
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in samples.indices) {
            val x = samples[i].toDouble()
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            out[i] = y
            x2 = x1; x1 = x; y2 = y1; y1 = y
        }
        return out
    }
}
