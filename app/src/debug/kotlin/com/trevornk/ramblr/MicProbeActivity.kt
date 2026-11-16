package com.trevornk.ramblr

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Debug-only mic probe: records the same phrase once per audio source and built-in mic, then
 * scores each take ([MicProbeScoring]) and transcribes it with the installed local model.
 *
 * Ramblr records with AudioSource.MIC and lets the platform pick the mic. This screen answers
 * whether a different source or mic would transcribe better on a given phone before any
 * production code changes. Takes are sequential (Android won't reliably give one app two
 * captures at once), so run the sweep two or three times and compare the pattern, not one take.
 *
 * Output, readable with `run-as <package>`:
 *   files/mic_probe/<session>_<n>.wav   each take, 16 kHz mono
 *   files/mic_probe/results.jsonl       one line per take
 *
 * Launch: the "Ramblr Mic Probe" launcher icon (debug builds only), or
 *   adb -s <serial> shell am start -n <package>/com.trevornk.ramblr.MicProbeActivity
 */
class MicProbeActivity : Activity() {

    private data class ProbeConfig(val label: String, val source: Int, val device: AudioDeviceInfo?)

    private data class Take(val config: ProbeConfig, val routed: String, val samples: ShortArray)

    private lateinit var phraseField: EditText
    private lateinit var statusView: TextView
    private lateinit var resultsView: TextView
    private lateinit var runButton: Button

    @Volatile private var running = false
    @Volatile private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Mic probe (debug)"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        })
        root.addView(label("Say this phrase once per take, the same way each time:"))
        phraseField = EditText(this).apply {
            setText(DEFAULT_PHRASE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 16f
        }
        root.addView(phraseField)
        root.addView(label("Configurations on this phone:"))
        root.addView(TextView(this).apply {
            text = configs().joinToString("\n") { "• ${it.label}" }
            textSize = 13f
        })
        runButton = Button(this).apply {
            text = "Run sweep"
            setOnClickListener { startSweep() }
        }
        root.addView(runButton)
        statusView = TextView(this).apply {
            textSize = 18f
            setPadding(0, 16, 0, 16)
        }
        root.addView(statusView)
        resultsView = TextView(this).apply {
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(resultsView)

        setContentView(
            ScrollView(this).apply { addView(root) },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        status("Model: ${modelName() ?: "none installed (scores only, no transcripts)"}")
    }

    override fun onDestroy() {
        destroyed = true
        super.onDestroy()
    }

    private fun startSweep() {
        if (running) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        running = true
        runButton.isEnabled = false
        val phrase = phraseField.text.toString()
        thread(name = "MicProbe") {
            try {
                sweep(phrase)
            } catch (e: Exception) {
                Log.e(TAG, "Sweep failed", e)
                status("Sweep failed: ${e.message}")
            } finally {
                running = false
                runOnUiThread { runButton.isEnabled = true }
            }
        }
    }

    private fun sweep(phrase: String) {
        val session = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outDir = File(filesDir, "mic_probe").apply { mkdirs() }
        val configs = configs()
        val takes = mutableListOf<Take>()
        for ((i, config) in configs.withIndex()) {
            for (s in COUNTDOWN_SECONDS downTo 1) {
                if (destroyed) return
                status("Take ${i + 1}/${configs.size}: ${config.label}\nGet ready… $s")
                Thread.sleep(1000)
            }
            status("Take ${i + 1}/${configs.size}: ${config.label}\nSPEAK NOW")
            val take = record(config)
            if (take == null) {
                appendResult("${i + 1}. ${config.label}: could not open this source\n")
                continue
            }
            takes += take
            File(outDir, "${session}_${i + 1}.wav").writeBytes(WavWriter.encode(toBytes(take.samples)))
        }
        if (destroyed) return

        status("Transcribing ${takes.size} takes…")
        val model = modelName()
        val transcriber = model?.let { LocalTranscriber.create(this, it) }
        val rows = try {
            takes.mapIndexed { i, take ->
                val text = transcriber?.transcribe(toFloats(take.samples), SAMPLE_RATE)
                val score = MicProbeScoring.score(take.samples, SAMPLE_RATE)
                val errors = text?.let { MicProbeScoring.wordErrors(phrase, it) }
                val row = JSONObject()
                    .put("session", session)
                    .put("take", i + 1)
                    .put("config", take.config.label)
                    .put("routed", take.routed)
                    .put("model", model ?: JSONObject.NULL)
                    .put("hf_fraction", score.hfFraction.toDouble())
                    .put("snr_db", score.snrDb.toDouble())
                    .put("peak_dbfs", score.peakDbfs.toDouble())
                    .put("clipped_fraction", score.clippedFraction.toDouble())
                    .put("word_errors", errors?.errors ?: JSONObject.NULL)
                    .put("reference_words", errors?.referenceWords ?: JSONObject.NULL)
                    .put("phrase", phrase)
                    .put("transcript", text ?: JSONObject.NULL)
                File(outDir, "results.jsonl").appendText(row.toString() + "\n")
                Log.i(TAG, row.toString())
                Triple(take, score, Pair(text, errors))
            }
        } finally {
            transcriber?.release()
        }

        val sb = StringBuilder("Session $session\n\n")
        for ((take, score, heard) in rows.sortedWith(compareBy({ it.third.second?.errors ?: 0 }, { -it.second.hfFraction }))) {
            val (text, errors) = heard
            sb.append(take.config.label).append('\n')
            sb.append("  routed: ").append(take.routed).append('\n')
            if (errors != null) sb.append("  errors: ${errors.errors}/${errors.referenceWords} words\n")
            sb.append("  hf ${"%.3f".format(score.hfFraction)}  snr ${"%.1f".format(score.snrDb)} dB")
            sb.append("  peak ${"%.1f".format(score.peakDbfs)} dBFS")
            if (score.clippedFraction > 0f) sb.append("  clipped ${"%.2f".format(score.clippedFraction * 100)}%")
            sb.append('\n')
            if (text != null) sb.append("  heard: ").append(text).append('\n')
            sb.append('\n')
        }
        appendResult(sb.toString())
        status("Done. Best first. Saved to files/mic_probe/")
    }

    private fun record(config: ProbeConfig): Take? {
        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufSize <= 0) return null
        val recorder = try {
            AudioRecord(config.source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4)
        } catch (e: Exception) {
            Log.w(TAG, "Could not create recorder for ${config.label}", e)
            return null
        }
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return null
            config.device?.let { recorder.setPreferredDevice(it) }
            recorder.startRecording()
            val samples = ShortArray(SAMPLE_RATE * TAKE_SECONDS)
            var read = 0
            var routed = "unknown"
            while (read < samples.size && !destroyed) {
                val n = recorder.read(samples, read, minOf(bufSize, samples.size - read))
                if (n < 0) {
                    Log.w(TAG, "read() returned $n for ${config.label}")
                    break
                }
                read += n
                // setPreferredDevice is only a request; report where the audio actually came from.
                if (routed == "unknown") recorder.routedDevice?.let { routed = describe(it) }
            }
            recorder.stop()
            return Take(config, routed, samples.copyOf(read))
        } finally {
            recorder.release()
        }
    }

    private fun configs(): List<ProbeConfig> {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val list = mutableListOf(
            ProbeConfig("MIC, default mic (what Ramblr uses now)", MediaRecorder.AudioSource.MIC, null),
            ProbeConfig("VOICE_RECOGNITION, default mic", MediaRecorder.AudioSource.VOICE_RECOGNITION, null),
        )
        if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            list += ProbeConfig("UNPROCESSED, default mic", MediaRecorder.AudioSource.UNPROCESSED, null)
        }
        list += ProbeConfig("CAMCORDER, default mic", MediaRecorder.AudioSource.CAMCORDER, null)
        val builtIn = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        // A single built-in device is already covered by the default rows above.
        if (builtIn.size > 1) {
            builtIn.forEach { list += ProbeConfig("MIC, preferred ${describe(it)}", MediaRecorder.AudioSource.MIC, it) }
        }
        return list
    }

    private fun describe(d: AudioDeviceInfo): String {
        val address = d.address.takeIf { it.isNotBlank() }?.let { " \"$it\"" } ?: ""
        val channels = d.channelCounts.takeIf { it.isNotEmpty() }?.let { " ch=${it.joinToString("/")}" } ?: ""
        return "id=${d.id} type=${d.type}$address$channels"
    }

    private fun modelName(): String? {
        val configured = getSharedPreferences("ramblr", Context.MODE_PRIVATE).getString("model_name", "") ?: ""
        if (configured.isNotBlank() && ModelDownloader.isInstalledDir(File(filesDir, "models/$configured"))) {
            return configured
        }
        return LocalTranscriber.availableModels(this).firstOrNull()
    }

    private fun toBytes(samples: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buf.putShort(it) }
        return buf.array()
    }

    private fun toFloats(samples: ShortArray) = FloatArray(samples.size) { samples[it] / 32768f }

    private fun status(text: String) = runOnUiThread { statusView.text = text }

    private fun appendResult(text: String) = runOnUiThread { resultsView.append(text) }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setPadding(0, 16, 0, 4)
    }

    companion object {
        private const val TAG = "MicProbe"
        private const val SAMPLE_RATE = AudioChunkPlanner.SAMPLE_RATE
        private const val TAKE_SECONDS = 7
        private const val COUNTDOWN_SECONDS = 3
        // Heavy on s, th, sh, f and k sounds, which are the ones a dull mic loses first.
        private const val DEFAULT_PHRASE =
            "Thanks, Sam. Six thick fish slid past the shed, so ship the fix this Thursday."
    }
}
