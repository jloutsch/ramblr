package com.trevornk.ramblr

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreHistoryToggleTest {
    private val app = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("ramblr", Context.MODE_PRIVATE)

    @Before fun historyOff() {
        prefs.edit().clear().putBoolean("dictation_history_enabled", false).commit()
    }

    private fun backupWithPrefs(vararg booleans: Pair<String, Boolean>): ByteArray {
        val xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n" +
            booleans.joinToString("") { (k, v) -> "    <boolean name=\"$k\" value=\"$v\" />\n" } + "</map>\n"
        return ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry(BackupManager.ENTRY_PREFS))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()
    }

    @Test fun `restoring a backup without the history key keeps history off`() {
        BackupManager.restoreBackup(app, backupWithPrefs("use_post_processing" to true).inputStream())

        assertEquals(false, prefs.getBoolean("dictation_history_enabled", true))
        assertEquals(true, prefs.getBoolean("use_post_processing", false))
    }

    @Test fun `a backup that says history is on still turns it on`() {
        BackupManager.restoreBackup(app, backupWithPrefs("dictation_history_enabled" to true).inputStream())

        assertEquals(true, prefs.getBoolean("dictation_history_enabled", false))
    }
}
