package com.trevornk.ramblr

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** "Translate to" inside the keyboard's style picker, the chip that reflects it, the switch key's
 *  long-press. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RamblrImeTranslationPickerTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private fun prefs() = app.getSharedPreferences("ramblr", Context.MODE_PRIVATE)

    @Before fun setUp() {
        prefs().edit().clear().putBoolean("local_cleanup_consent_seen", true).apply()
        KeyboardTranslation.setChosen(app, listOf("de", "fr"))
    }
    @After fun tearDown() { prefs().edit().clear().apply() }

    private val editor = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
        packageName = "harness.example"
        fieldId = 42
    }

    private fun panel(): Pair<RamblrImeService, View> {
        val service = Robolectric.buildService(RamblrImeService::class.java).create().get()
        val root = service.onCreateInputView()
        service.onStartInput(editor, false)
        service.onStartInputView(editor, false)
        return service to root
    }

    private fun View.all(): List<View> =
        if (this is ViewGroup) listOf(this) + (0 until childCount).flatMap { getChildAt(it).all() } else listOf(this)

    private fun View.chip(): TextView =
        all().filterIsInstance<TextView>().first {
            val d = it.contentDescription?.toString().orEmpty()
            d.startsWith("Cleanup style") || d.startsWith("Cleanup is off") || d.startsWith("Translating to")
        }

    private fun View.textView(text: String): TextView =
        all().filterIsInstance<TextView>().firstOrNull { it.text.toString() == text }
            ?: error("no '$text' among ${all().filterIsInstance<TextView>().map { it.text }}")

    private fun render(service: RamblrImeService, state: ImeUiState) {
        RamblrImeService::class.java.getDeclaredMethod("renderState", ImeUiState::class.java)
            .apply { isAccessible = true }.invoke(service, state)
    }

    @Test fun `without a translation the chip reads exactly as before`() {
        val (_, root) = panel()
        assertEquals("Cleanup off", root.chip().text.toString())
        assertTrue(root.chip().contentDescription.toString().startsWith("Cleanup is off"))
        PostProcessingToggle.setEnabled(app, true)
        val (_, on) = panel()
        assertEquals(CleanupStyleMenu.load(app, "harness.example").current.title, on.chip().text.toString())
        assertTrue(on.chip().contentDescription.toString().startsWith("Cleanup style"))
    }

    @Test fun `the picker has a Translate to row with Original and the chosen languages`() {
        val (_, root) = panel()
        root.chip().performClick()

        root.textView("Translate to")
        root.textView("Original")
        root.textView("Deutsch")
        root.textView("Français")
        root.textView("Languages…")
    }

    @Test fun `picking a language sets the sticky target, closes the picker and marks the chip`() {
        val (_, root) = panel()
        root.chip().performClick()

        root.textView("Deutsch").performClick()

        assertEquals("de", KeyboardTranslation.target(app)?.code)
        val chip = root.chip()
        assertTrue("chip must show the target first: ${chip.text}", chip.text.toString().startsWith("→ DE"))
        assertTrue(chip.contentDescription.toString().startsWith("Translating to German"))
    }

    @Test fun `Original turns translation back off`() {
        KeyboardTranslation.setTarget(app, "de")
        val (_, root) = panel()
        assertTrue(root.chip().text.toString().startsWith("→ DE"))
        root.chip().performClick()

        root.textView("Original").performClick()

        assertNull(KeyboardTranslation.target(app))
        assertFalse(root.chip().text.toString().contains("→"))
    }

    @Test fun `the selected language is announced as selected`() {
        KeyboardTranslation.setTarget(app, "fr")
        val (_, root) = panel()
        root.chip().performClick()
        assertTrue(root.textView("Français").contentDescription.toString().endsWith("selected"))
        assertFalse(root.textView("Deutsch").contentDescription.toString().endsWith("selected"))
    }

    @Test fun `the picker, and with it Translate to, is unavailable while a dictation is in flight`() {
        for (state in listOf(ImeUiState.RECORDING, ImeUiState.TRANSCRIBING, ImeUiState.CLEANING)) {
            val (service, root) = panel()
            root.chip().performClick() // open
            render(service, state)
            assertFalse("$state: chip disabled", root.chip().isEnabled)
            assertTrue(root.chip().contentDescription.toString().contains("when dictation finishes"))
            // The picker was dismissed, so its translate row is not on screen to be tapped.
            assertNotEquals(View.VISIBLE, root.textView("Deutsch").visibleChainVisibility())
            // A direct call (e.g. a stale click) must not change the target mid-flight either.
            val m = RamblrImeService::class.java.getDeclaredMethod("onPickerTranslation", TranslationLanguage::class.java)
                .apply { isAccessible = true }
            m.invoke(service, KeyboardTranslation.byCode("de"))
            assertNull("$state: target unchanged", KeyboardTranslation.target(app))
        }
    }

    private fun assertNotEquals(a: Int, b: Int) = assertTrue("expected different, both $a", a != b)

    private fun View.visibleChainVisibility(): Int {
        var v: View? = this
        while (v != null) {
            if (v.visibility != View.VISIBLE) return v.visibility
            v = v.parent as? View
        }
        return View.VISIBLE
    }

    @Test fun `the picker warns when there is no cloud provider with a key`() {
        val (_, root) = panel()
        root.chip().performClick()
        assertTrue(root.all().filterIsInstance<TextView>().any { it.visibility == View.VISIBLE && it.text.toString().startsWith("Translation needs a cloud AI provider") })
    }

    @Test fun `a first translation asks for the off-device consent inside the panel`() {
        prefs().edit().putBoolean("local_cleanup_consent_seen", false).putBoolean("use_local", true).apply()
        val (_, root) = panel()
        root.chip().performClick()

        root.textView("Deutsch").performClick()
        assertNull("not set until the user agrees", KeyboardTranslation.target(app))
        assertTrue(root.all().filterIsInstance<TextView>().any { it.visibility == View.VISIBLE && it.text.toString().startsWith("Send text to") })

        root.textView("Not now").performClick()
        assertNull(KeyboardTranslation.target(app))
        assertFalse(prefs().getBoolean("local_cleanup_consent_seen", false))

        root.chip().performClick()
        root.textView("Deutsch").performClick()
        root.textView("Allow").performClick()
        assertEquals("de", KeyboardTranslation.target(app)?.code)
        assertTrue(prefs().getBoolean("local_cleanup_consent_seen", false))
    }

    @Test fun `a language dropped in Settings stops translating and the chip says so`() {
        KeyboardTranslation.setTarget(app, "de")
        KeyboardTranslation.setChosen(app, listOf("fr"))
        val (_, root) = panel()
        assertFalse(root.chip().text.toString().contains("→"))
    }

    @Test fun `the switch key has a long-press action, which the on-device check exercises`() {
        val (_, root) = panel()
        val key = root.all().filterIsInstance<ImageButton>().first { it.contentDescription?.toString() == "Switch keyboard" }
        assertTrue(key.isLongClickable)
        // The long-press consumes the gesture (so it is not also a tap that switches away).
        assertTrue(key.performLongClick())
    }
}
