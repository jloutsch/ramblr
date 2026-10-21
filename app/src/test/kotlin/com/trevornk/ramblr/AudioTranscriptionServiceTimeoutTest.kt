package com.trevornk.ramblr

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioTranscriptionServiceTimeoutTest {

    @Test fun `after the system times the service out the worker takes no more queued jobs`() {
        val app = RuntimeEnvironment.getApplication()
        val store = AudioJobs.store(app)
        listOf("a", "b").forEach { store.upsert(AudioJob(it, it, JobKind.IMPORTED, JobStatus.QUEUED, 1, hasLocalAudio = true)) }

        val controller = Robolectric.buildService(AudioTranscriptionService::class.java).create()
        val service = controller.get()
        service.onTimeout(1)
        service.onStartCommand(null, 0, 2)
        // The worker runs on its own thread. Without the timeout check it fails both jobs at once
        // (their audio files are missing).
        Thread.sleep(500)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(JobStatus.QUEUED, JobStatus.QUEUED), listOf("a", "b").map { store.get(it)!!.status })
        controller.destroy()
    }
}
