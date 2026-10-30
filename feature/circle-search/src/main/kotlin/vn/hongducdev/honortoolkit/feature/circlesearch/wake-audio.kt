package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.IOException
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Microphone loop from Trợ lý VXM's `WakeAudio`: 16 kHz mono PCM16, 1280-sample buffers (~80 ms),
 * one worker thread, recognizer callbacks posted to the main thread.
 */
internal class WakeAudio(
    private val recognizer: Recognizer,
    private val sampleRate: Float = SAMPLE_RATE,
    private val playback: PlaybackGate? = null,
    private val filter: WakeFilter? = null,
) {
    private val main = Handler(Looper.getMainLooper())
    private val level = WakeLevel()
    private val recorder: AudioRecord

    @Volatile
    private var running = false
    private var worker: Thread? = null

    init {
        val minimum = AudioRecord.getMinBufferSize(
            sampleRate.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate.toInt(),
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minimum, 8192),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("Micro chưa sẵn sàng")
        }
    }

    fun startListening(listener: RecognitionListener) {
        running = true
        worker = Thread({ loop(listener) }, "wake-audio").also { it.start() }
    }

    private fun loop(listener: RecognitionListener) {
        val buffer = ShortArray(BUFFER_SAMPLES)
        var gate = SpeechGate()
        var gating = filter != null
        var blocked = false
        var lastHeartbeat = 0L

        try {
            recorder.startRecording()
            while (running) {
                val length = recorder.read(buffer, 0, buffer.size)
                if (!running) break
                if (length < 0) throw IOException("Lỗi đọc micro: $length")
                if (length == 0) continue

                if (playback?.blocks() == true) {
                    if (!blocked) {
                        recognizer.reset()
                        gate = SpeechGate()
                        filter?.invalidateAudio()
                    }
                    blocked = true
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastHeartbeat >= 1000) {
                        lastHeartbeat = now
                        main.post { if (running) listener.onPartialResult(EMPTY_PARTIAL) }
                    }
                    continue
                }

                if (blocked) {
                    recognizer.reset()
                    gate = SpeechGate()
                    filter?.invalidateAudio()
                    blocked = false
                }

                val now = SystemClock.elapsedRealtime()
                level.add(buffer, length, now)
                filter?.push(buffer, length)

                val input = if (gating) gate.prepare(buffer, length) else buffer
                if (input == null) {
                    if (now - lastHeartbeat >= 1000) {
                        lastHeartbeat = now
                        main.post { if (running) listener.onPartialResult(EMPTY_PARTIAL) }
                    }
                    continue
                }

                val count = if (input === buffer) length else input.size
                val complete = recognizer.acceptWaveForm(input, count)
                val result = if (complete) recognizer.getResult() else recognizer.getPartialResult()
                if (gating && gate.ended) recognizer.reset()
                val epoch = filter?.audioEpoch() ?: 0
                main.post {
                    if (!running) return@post
                    if (filter == null || epoch == filter.audioEpoch()) {
                        if (complete) listener.onResult(result) else listener.onPartialResult(result)
                    }
                }
            }
        } catch (error: Exception) {
            if (running) main.post { if (running) listener.onError(error) }
        } finally {
            runCatching { recorder.stop() }
        }
    }

    fun cancel() {
        running = false
        runCatching { recorder.stop() }
        val thread = worker
        if (thread != null) {
            var interrupted = false
            while (thread.isAlive) {
                try {
                    thread.join()
                } catch (error: InterruptedException) {
                    interrupted = true
                }
            }
            if (interrupted) Thread.currentThread().interrupt()
        }
        main.removeCallbacksAndMessages(null)
    }

    fun shutdown() {
        runCatching { recorder.release() }
    }

    fun loudEnough(sensitivity: Int): Boolean = level.allows(sensitivity, SystemClock.elapsedRealtime())

    fun displayLevels(): IntArray = level.display(SystemClock.elapsedRealtime())

    private companion object {
        const val SAMPLE_RATE = 16000f
        const val BUFFER_SAMPLES = 1280
        const val EMPTY_PARTIAL = """{"partial":""}"""
    }
}

/**
 * Blocks wake detection while media plays (or for one second after it stops) so the recognizer does
 * not hear a video saying "OK Google".
 */
internal class PlaybackGate(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var lastMediaAt = -1000L

    @Synchronized
    fun blocks(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val outputActive = audio.isMusicActive || audio.activePlaybackConfigurations.isNotEmpty()
        if (outputActive) lastMediaAt = now
        return now - lastMediaAt < 1000
    }
}

/**
 * Simple energy-based voice activity gate from Trợ lý VXM's `SpeechGate`. Silence is not fed to
 * Vosk, which saves battery and prevents the decoder from inventing words from room noise.
 */
internal class SpeechGate {
    var ended = false
        private set

    private val history = ShortArray(12800)
    private val noise = DoubleArray(40)
    private var noiseCount = 0
    private var noisePos = 0
    private var pos = 0
    private var tail = 0
    private var filled = 0
    private var fed = 0L
    private var total = 0L

    fun prepare(audio: ShortArray, length: Int): ShortArray? {
        var energy = 0.0
        for (i in 0 until length) {
            val sample = audio[i].toDouble()
            energy += sample * sample
        }
        val db = log10(max(1.0, sqrt(energy / length)) / 32768.0) * 20.0
        noise[noisePos] = db
        noisePos = (noisePos + 1) % noise.size
        noiseCount = min(noise.size, noiseCount + 1)

        var floor = -65.0
        if (noiseCount >= 10) {
            floor = noise[0]
            for (i in 1 until noiseCount) floor = min(floor, noise[i])
        }
        val threshold = max(-60.0, min(-48.0, 3.0 + floor))
        val voice = db >= threshold
        val wasActive = tail > 0
        tail = if (voice) 16000 else max(0, tail - length)
        ended = wasActive && tail == 0
        val active = wasActive || voice

        var output: ShortArray? = if (active) audio else null
        if (active && !wasActive) {
            val count = min(filled.toLong(), total - fed).toInt()
            output = ShortArray(count + length)
            val start = (pos - count + history.size) % history.size
            for (i in 0 until count) output[i] = history[(start + i) % history.size]
            System.arraycopy(audio, 0, output, count, length)
        }
        for (i in 0 until length) {
            history[pos] = audio[i]
            pos = (pos + 1) % history.size
        }
        filled = min(history.size, filled + length)
        total += length
        if (active) fed = total
        return output
    }
}
