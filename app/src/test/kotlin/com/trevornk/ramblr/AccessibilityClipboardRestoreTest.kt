package com.trevornk.ramblr

import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A paste-style dictation leaves a delayed clipboard restore behind; the next dictation must not drop it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilityClipboardRestoreTest {
    private val app = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        prefs().edit().clear().apply()
        PreviewBeforeInjectToggle.setEnabled(app, false)
        DictationHistoryStore.forContext(app).clear()
    }

    @After fun tearDown() { prefs().edit().clear().apply() }

    private fun prefs() = app.getSharedPreferences("ramblr", Context.MODE_PRIVATE)

    private fun clipboardText(): String? =
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text?.toString()

    private fun field(name: String) =
        WhisperAccessibilityService::class.java.getDeclaredField(name).apply { isAccessible = true }

    @Test fun `a second dictation inside the restore delay still hands back what the user had copied`() {
        val service = Robolectric.buildService(WhisperAccessibilityService::class.java, null).create().get()
        val restore = WhisperAccessibilityService::class.java
            .getDeclaredMethod("restoreClipboardAfterInjection", String::class.java, String::class.java)
            .apply { isAccessible = true }

        // State right after dictation 1 was pasted: its text is on the clipboard and the restore of
        // the user's "PRIOR" is waiting out its delay (finishInjection's Delayed branch).
        ClipboardUtil.copy(app, "one")
        val pending = Runnable {
            field("pendingClipboardRestore").set(service, null)
            restore.invoke(service, "one", "PRIOR")
        }
        field("pendingClipboardRestore").set(service, pending)
        field("handler").get(service).let { (it as android.os.Handler).postDelayed(pending, 30_000) }

        // Dictation 2, through the same runtime funnel a real dictation takes.
        WhisperAccessibilityService::class.java.getDeclaredMethod("showOverlay").apply { isAccessible = true }.invoke(service)
        val listener = field("runtimeListener").get(service) as RuntimeListener
        listener.onEnterTranscribingUi()
        listener.deliverText("two", null, null, null, 2000)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("PRIOR", clipboardText())
        assertNull(field("pendingClipboardRestore").get(service))
    }
}
