package com.trevornk.ramblr

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The processing ring around the locked mic, and the second-tap guard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImeMicProcessingTest {

    private fun View.all(): Sequence<View> = sequence {
        yield(this@all)
        if (this@all is ViewGroup) for (i in 0 until childCount) yieldAll(getChildAt(i).all())
    }

    private fun panel(): Pair<RamblrImeService, View> {
        val editor = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "harness.example"
            fieldId = 7
        }
        val service = Robolectric.buildService(RamblrImeService::class.java).create().get()
        val root = service.onCreateInputView()
        service.onStartInput(editor, false)
        service.onStartInputView(editor, false)
        return service to root
    }

    private fun RamblrImeService.render(state: ImeUiState) {
        javaClass.getDeclaredMethod("renderState", ImeUiState::class.java).apply { isAccessible = true }.invoke(this, state)
    }

    @Test fun `a ring spins around the locked mic only while the recording is processed`() {
        val (service, root) = panel()
        val mic = root.all().filterIsInstance<ImageButton>().first { it.isClickable && it.contentDescription != null }
        val ring = root.all().first { it.tag == RamblrImeService.MIC_PROGRESS_TAG }
        assertEquals(View.GONE, ring.visibility)

        for (state in listOf(ImeUiState.TRANSCRIBING, ImeUiState.CLEANING)) {
            service.render(state)
            assertEquals("$state shows the ring", View.VISIBLE, ring.visibility)
            assertFalse("$state locks the mic", mic.isEnabled)
        }
        for (state in listOf(ImeUiState.IDLE, ImeUiState.RECORDING, ImeUiState.ERROR, ImeUiState.SECURE_FIELD)) {
            service.render(state)
            assertEquals("$state hides the ring", View.GONE, ring.visibility)
        }
        service.render(ImeUiState.IDLE)
        assertTrue(mic.isEnabled)
        assertFalse("the ring never takes a tap", ring.isClickable)
    }

    @Test fun `processing means transcribing or cleaning`() {
        assertEquals(
            setOf(ImeUiState.TRANSCRIBING, ImeUiState.CLEANING),
            ImeUiState.values().filter(::imeIsProcessing).toSet(),
        )
    }

    @Test fun `a second tap during processing is ignored even through accessibility`() {
        val (service, root) = panel()
        val mic = root.all().filterIsInstance<ImageButton>().first { it.isClickable && it.contentDescription != null }
        service.render(ImeUiState.TRANSCRIBING)
        // performClick on a disabled view still runs the listener; the listener itself must refuse.
        assertFalse(mic.isEnabled)
        assertFalse("tap during processing must not start a recording", imeMicEnabled(ImeUiState.TRANSCRIBING))
        assertFalse(imeMicEnabled(ImeUiState.CLEANING))
        mic.performClick()
        assertEquals(ImeUiState.TRANSCRIBING, service.lastRenderedStateForTest())
    }
}
