package com.trevornk.ramblr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A runtime error used to be painted over by the idle repaint that follows it at once. */
class ImePanelControllerMessageTest {

    private class Ui {
        val states = mutableListOf<ImeUiState>()
        val messages = mutableListOf<String>()
        var translation: String? = null
        val levels = mutableListOf<Float>()
    }

    private val identity = ImeEditorIdentity("com.example.app", 0, 0)

    private fun controller(ui: Ui, withLevels: Boolean = false) = ImePanelController(
        runtime = object : ImeRuntimeControl {
            override fun onTap() {}
            override fun invalidate() {}
            override fun teardownAsync() {}
        },
        renderState = { ui.states.add(it) },
        editorSnapshot = { Triple(1L, identity, Any()) },
        commitText = { _, _ -> true },
        userMessage = { ui.messages.add(it) },
        translationLanguage = { ui.translation },
        renderLevel = if (withLevels) { l -> ui.levels.add(l) } else null,
        translationFailedMessage = "Translation failed",
    ).also { it.onEditorChanged(1L, identity, Any()) }

    @Test fun `an error stays on screen after the idle repaint`() {
        val ui = Ui()
        val listener = controller(ui).listener

        listener.onUserMessage("Error: model overloaded")
        listener.onIdleUi()

        assertEquals(ImeUiState.IDLE, ui.states.last())
        assertEquals("Error: model overloaded", ui.messages.last())
    }

    @Test fun `the next mic tap clears it`() {
        val ui = Ui()
        val listener = controller(ui).listener
        listener.onUserMessage("No speech detected")
        listener.onIdleUi()
        val shown = ui.messages.size

        listener.onRecordingStartRequested()
        listener.onIdleUi()

        assertEquals(shown, ui.messages.size)
    }

    @Test fun `an idle repaint without a message shows nothing extra`() {
        val ui = Ui()
        controller(ui).listener.onIdleUi()

        assertEquals(listOf(ImeUiState.IDLE), ui.states)
        assertEquals(emptyList<String>(), ui.messages)
    }

    @Test fun `a cleanup failure notice survives the idle repaint too`() {
        val ui = Ui()
        val listener = controller(ui).listener
        listener.onRecordingStartRequested()

        listener.deliverText("raw words", rawText = null, paidFallbackGroup = null, cleanupError = "timeout", feedbackDurationMs = 0)
        listener.onIdleUi()

        assertEquals("Cleanup failed — inserted raw transcript", ui.messages.last())
    }

    @Test fun `a message does not follow the user into another field or a hidden keyboard`() {
        val ui = Ui()
        val c = controller(ui)
        c.listener.onUserMessage("Error: x")
        c.onEditorChanged(2L, identity.copy(fieldId = 9), Any())
        val shown = ui.messages.size
        c.listener.onIdleUi()
        assertEquals("a new field shows its own idle text", shown, ui.messages.size)

        c.listener.onUserMessage("Error: y")
        c.onLifecycleLost(ImeLifecycleLoss.HIDDEN)
        c.onEditorChanged(3L, identity.copy(fieldId = 9), Any())
        val after = ui.messages.size
        c.listener.onIdleUi()
        assertEquals(after, ui.messages.size)
    }

    @Test fun `the translate target is read when the dictation starts, so a later change waits for the next one`() {
        val ui = Ui()
        val listener = controller(ui).listener
        assertNull(listener.translationTarget())

        ui.translation = "German"
        listener.onRecordingStartRequested()
        assertEquals("German", listener.translationTarget())

        ui.translation = "French" // picked mid-flight (should be impossible in the UI; the controller still holds)
        assertEquals("German", listener.translationTarget())

        ui.translation = null
        listener.onRecordingStartRequested()
        assertNull(listener.translationTarget())
    }

    @Test fun `a failed translating cleanup says translation failed, not just cleanup`() {
        val ui = Ui()
        val listener = controller(ui).listener
        ui.translation = "German"
        listener.onRecordingStartRequested()

        listener.deliverText("hello", rawText = null, paidFallbackGroup = null, cleanupError = "timeout", feedbackDurationMs = 0)
        listener.onIdleUi()

        assertEquals("Translation failed", ui.messages.last())
    }

    @Test fun `the level meter runs only when the panel asked for it`() {
        val ui = Ui()
        assertFalse(controller(ui, withLevels = false).listener.wantsAudioLevel())

        val withMeter = controller(ui, withLevels = true).listener
        assertTrue(withMeter.wantsAudioLevel())
        withMeter.onAudioLevel(0.4f)
        assertEquals(listOf(0.4f), ui.levels)
    }
}
