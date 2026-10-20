package com.trevornk.ramblr

import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The sound waves inside the keyboard's mic button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MicWaveViewTest {

    private fun View.all(): Sequence<View> = sequence {
        yield(this@all)
        if (this@all is ViewGroup) for (i in 0 until childCount) yieldAll(getChildAt(i).all())
    }

    @Test fun `levels are clamped and the newest one wins`() {
        val wave = MicWaveView(RuntimeEnvironment.getApplication())
        wave.setActive(true)
        wave.push(0.2f)
        assertEquals(0.2f, wave.targetForTest(), 0f)
        wave.push(1.4f)
        assertEquals(1f, wave.targetForTest(), 0f)
        wave.push(-3f)
        assertEquals(0f, wave.targetForTest(), 0f)
    }

    @Test fun `stopping forgets the level so the next recording starts flat`() {
        val wave = MicWaveView(RuntimeEnvironment.getApplication())
        wave.setActive(true)
        wave.push(0.8f)
        wave.setActive(false)
        wave.setActive(true)
        assertEquals(0f, wave.targetForTest(), 0f)
    }

    @Test fun `system animations off makes the wave static`() {
        val app = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val wave = MicWaveView(app)
        wave.setActive(true)
        assertTrue(wave.reducedMotionForTest())
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        wave.setActive(false)
        wave.setActive(true)
        assertFalse(wave.reducedMotionForTest())
    }

    @Test fun `waves replace the mic glyph only while recording and never take the tap`() {
        val editor = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            packageName = "harness.example"
            fieldId = 5
        }
        val service = Robolectric.buildService(RamblrImeService::class.java).create().get()
        val root = service.onCreateInputView()
        service.onStartInput(editor, false)
        service.onStartInputView(editor, false)
        val mic = root.all().filterIsInstance<ImageButton>().first { it.isClickable && it.contentDescription != null }
        val wave = root.all().filterIsInstance<MicWaveView>().single()
        assertFalse(wave.isClickable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, wave.importantForAccessibility)
        assertNotNull(mic.drawable)

        val render = service.javaClass.getDeclaredMethod("renderState", ImeUiState::class.java).apply { isAccessible = true }
        render.invoke(service, ImeUiState.RECORDING)
        assertNull("the glyph makes way for the waves", mic.drawable)
        wave.push(0.6f)
        assertEquals(0.6f, wave.targetForTest(), 0f)

        render.invoke(service, ImeUiState.TRANSCRIBING)
        assertNotNull(mic.drawable)
        assertEquals("waves stop with the recording", 0f, wave.targetForTest(), 0f)
    }
}
