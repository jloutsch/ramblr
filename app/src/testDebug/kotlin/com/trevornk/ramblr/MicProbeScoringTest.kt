package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class MicProbeScoringTest {

    private val rate = 16_000

    private fun sine(hz: Double, seconds: Double, amplitude: Double = 0.5): ShortArray =
        ShortArray((rate * seconds).toInt()) { i ->
            (sin(2 * PI * hz * i / rate) * amplitude * Short.MAX_VALUE).roundToInt().toShort()
        }

    @Test fun `a high tone keeps nearly all its energy above the cutoff`() {
        val score = MicProbeScoring.score(sine(6000.0, 1.0), rate)
        assertTrue("hf=${score.hfFraction}", score.hfFraction > 0.9f)
    }

    @Test fun `a low tone has almost no energy above the cutoff`() {
        val score = MicProbeScoring.score(sine(300.0, 1.0), rate)
        assertTrue("hf=${score.hfFraction}", score.hfFraction < 0.05f)
    }

    @Test fun `snr compares the loud frames with the quiet ones`() {
        // Half a second at 0.5 amplitude, half a second at 0.005: a 40 dB gap.
        val loud = sine(500.0, 0.5, 0.5)
        val quiet = sine(500.0, 0.5, 0.005)
        val score = MicProbeScoring.score(loud + quiet, rate)
        assertEquals(40f, score.snrDb, 1.5f)
    }

    @Test fun `digital silence scores zero instead of dividing by zero`() {
        val score = MicProbeScoring.score(ShortArray(rate), rate)
        assertEquals(0f, score.hfFraction, 0f)
        assertEquals(0f, score.snrDb, 0f)
    }

    @Test fun `a clip shorter than a quarter second is not scored`() {
        val score = MicProbeScoring.score(sine(6000.0, 0.2), rate)
        assertEquals(0f, score.hfFraction, 0f)
    }

    @Test fun `peak level and clipping are reported`() {
        val clipped = ShortArray(rate) { i -> if (i % 100 == 0) Short.MAX_VALUE else 1000 }
        val score = MicProbeScoring.score(clipped, rate)
        assertEquals(0f, score.peakDbfs, 0.01f)
        assertEquals(0.01f, score.clippedFraction, 0.001f)

        val half = MicProbeScoring.score(sine(500.0, 1.0, 0.5), rate)
        assertEquals(-6f, half.peakDbfs, 0.1f)
        assertEquals(0f, half.clippedFraction, 0f)
    }

    @Test fun `word errors ignore case and punctuation`() {
        val result = MicProbeScoring.wordErrors("Thanks, Sam!", "thanks sam")
        assertEquals(0, result.errors)
        assertEquals(2, result.referenceWords)
    }

    @Test fun `a misheard word counts as one substitution`() {
        assertEquals(1, MicProbeScoring.wordErrors("thanks sam", "tanks sam").errors)
    }

    @Test fun `dropped and extra words each count once`() {
        assertEquals(1, MicProbeScoring.wordErrors("six thick sharp", "six sharp").errors)
        assertEquals(1, MicProbeScoring.wordErrors("six sharp", "six very sharp").errors)
        assertEquals(2, MicProbeScoring.wordErrors("six sharp", "").errors)
    }

    @Test fun `apostrophes stay inside a word`() {
        assertEquals(0, MicProbeScoring.wordErrors("She's here.", "she's here").errors)
        assertEquals(1, MicProbeScoring.wordErrors("She's here.", "shes here").errors)
    }
}
