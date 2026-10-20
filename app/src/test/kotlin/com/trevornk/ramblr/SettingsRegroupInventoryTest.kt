package com.trevornk.ramblr

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The settings regroup moved rows between screens; it must not lose any. This lists every leaf row
 * title the settings screens had before the regroup (the screen-launcher rows Transcription,
 * Cleanup, Cloud, Invocation, Behavior, Advanced, Data & Logs and Overlay Appearance became
 * sections and screens of their own) and checks each is still on one of the screens, wherever it
 * lives now. Rows hidden by state (e.g. "Remove custom icon") count: they are in the view tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRegroupInventoryTest {

    @Before fun initWorkManager() {
        val app = RuntimeEnvironment.getApplication()
        if (!androidx.work.WorkManager.isInitialized()) {
            androidx.work.WorkManager.initialize(app, androidx.work.Configuration.Builder().build())
        }
    }

    private fun View.titles(into: MutableSet<String>) {
        if (this is TextView && tag == "title") into.add(text.toString())
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).titles(into)
    }

    private fun screens(): List<Intent> {
        val app = RuntimeEnvironment.getApplication()
        return listOf<Class<out Activity>>(
            MainActivity::class.java, SetupActivity::class.java, TranscriptionActivity::class.java,
            CleanupActivity::class.java, LivePreviewActivity::class.java, CloudProviderActivity::class.java,
            OverlayAppearanceActivity::class.java, AudioFilesActivity::class.java,
            SnippetManagerActivity::class.java, StyleManagerActivity::class.java,
        ).map { Intent(app, it) } +
            BehaviorActivity.Section.entries.map { BehaviorActivity.intent(app, it) } +
            InvocationActivity.Section.entries.map { InvocationActivity.intent(app, it) } +
            DataLogsActivity.Section.entries.map { DataLogsActivity.intent(app, it) }
    }

    @Test fun `every row the settings screens had before the regroup is still on a screen`() {
        val found = mutableSetOf<String>()
        for (intent in screens()) {
            @Suppress("UNCHECKED_CAST")
            val type = Class.forName(intent.component!!.className) as Class<Activity>
            Robolectric.buildActivity(type, intent).setup().get().window.decorView.titles(found)
        }
        val missing = BEFORE.filter { it !in found }
        assertTrue("rows lost in the regroup: $missing", missing.isEmpty())
    }

    private companion object {
        val BEFORE = listOf(
            // Main
            "Status", "Setup", "History",
            // Transcription (now Recognition)
            "Use cloud transcription", "Dictation language", "Local transcription threads", "Canary language",
            "Compress audio before cloud upload", "Live cloud transcription",
            // Cleanup (now AI cleanup)
            "Improve my dictation with AI", "Local", "Cloud", "Preview before inserting",
            "Remember cleanup style per app", "Offer raw text after cleanup",
            // Cloud providers
            "Add provider", "Fall back to on-device if cloud fails", "Fall back to cloud if on-device fails",
            // Live preview
            "Streaming live preview",
            // Personal vocabulary and snippets
            "Personal vocabulary", "Smart vocabulary suggestions", "Snippets", "Enable snippets", "+ New snippet",
            // Invocation (now System shortcuts / Voice keyboard) and the ring
            "Floating icon (default)", "System controls", "Ramblr keyboard", "Floating ring",
            "System accessibility button / gesture", "Volume-keys hold", "Quick Settings tile",
            "Direct shortcut control", "Turn Ramblr off",
            // Behavior (now Floating icon / Recording / Privacy)
            "Allow hiding the floating icon", "Let automation apps turn Ramblr off", "App exclusions",
            "Auto-hide icon when idle", "Auto-hide delay", "Peeked sliver size", "Single-tap restore and record",
            "Auto-stop after silence", "Silence threshold", "Show the icon",
            // Overlay Appearance (now Size and colors)
            "Icon size", "Border color", "Fill color (idle only)", "Microphone icon color",
            "Custom icon image", "Remove custom icon",
            // Data & Logs (now History / Backup & restore / Diagnostics)
            "Save dictation history", "Dictation history", "Backup all data", "Restore from backup",
            "Share benchmark log", "Save quality log", "Share quality log", "Debug / visibility",
            // Setup
            "Audio permission", "Accessibility service", "Notifications", "Redo setup walkthrough",
            // Audio files, styles
            "Transcribe an audio file", "Record a note", "+ New style",
        )
    }
}
