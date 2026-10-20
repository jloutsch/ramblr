package com.trevornk.ramblr

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioJobImportTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun runImport(bytes: Int, maxImportBytes: Long): AudioJob {
        val shared = File(app.cacheDir, "shared.mp3").apply { writeBytes(ByteArray(bytes) { 1 }) }
        val store = AudioJobStore(File(app.filesDir, "jobs.jsonl"))
        val job = AudioJob("j", "shared.mp3", JobKind.IMPORTED, JobStatus.QUEUED, 1, sourceUri = Uri.fromFile(shared).toString())
        store.upsert(job)
        AudioJobRunner(app, store, { false }, {}, maxImportBytes).run(job)
        return store.get("j")!!
    }

    @Test fun `a shared file over the import cap fails as too large and leaves no copy`() {
        val done = runImport(bytes = 1024, maxImportBytes = 1000)
        assertEquals(JobStatus.FAILED, done.status)
        assertEquals(JobFailure.TOO_LARGE, done.failure)
        val dest = AudioJobFiles.sourceFile(app.filesDir, "j")
        assertFalse(dest.exists())
        assertFalse(File(dest.parentFile, "${dest.name}.part").exists())
    }

    @Test fun `a shared file at the cap is imported`() {
        // Not audio, so it fails later at decode, but it gets past the cap.
        val done = runImport(bytes = 1000, maxImportBytes = 1000)
        assertNotEquals(JobFailure.TOO_LARGE, done.failure)
        assertEquals(true, done.hasLocalAudio)
    }
}
