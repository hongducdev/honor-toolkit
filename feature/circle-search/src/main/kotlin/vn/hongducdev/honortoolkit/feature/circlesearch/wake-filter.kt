package vn.hongducdev.honortoolkit.feature.circlesearch

import org.json.JSONObject

/**
 * Wake-word filter ported from Trợ lý VXM's `WakeFilter`, with one extra guard for this app.
 *
 * A partial result must stay stable for ~80 ms (1280 decoded samples at 16 kHz) before it fires.
 * A final result no longer fires on its own: it must be corroborated by an earlier matching
 * partial in the same audio epoch. Vosk's tiny word-list grammar will happily decode a cough, a
 * door or a TV into the wake phrase once it closes an utterance, so a bare final is the main
 * source of "Gemini opened by itself".
 */
internal class WakeFilter(
    private val canAccept: () -> Boolean,
    private val levelAllowed: () -> Boolean,
    private val onWake: (String) -> Unit,
    private val onObservation: (String) -> Unit = {},
) {
    private val lock = Any()

    @Volatile
    private var closed = false

    @Volatile
    private var epoch = 0

    private var samples = 0L
    private var fired = false
    private var candidateAt = -1L
    private var candidateEpoch = -1

    /** Only audio that was actually decoded counts; silence and playback gaps must not age a candidate. */
    fun push(audio: ShortArray, length: Int) {
        synchronized(lock) { samples += length }
    }

    /** Called when audio is dropped or the recognizer is reset; a stale candidate must not fire. */
    fun invalidateAudio() {
        epoch++
    }

    fun audioEpoch(): Int = epoch

    fun candidate(json: String, partial: Boolean) {
        if (closed || fired || !canAccept()) return
        val text = runCatching {
            JSONObject(json).optString(if (partial) "partial" else "text", "")
        }.getOrNull()?.trim().orEmpty()
        onObservation(text)

        if (!WakePhrase.matches(text) || !levelAllowed()) {
            candidateAt = -1L
            return
        }

        val now: Long
        val token: Int
        synchronized(lock) {
            now = samples
            token = epoch
        }
        val known = candidateAt >= 0 && candidateEpoch == token
        if (partial) {
            if (!known) {
                candidateAt = now
                candidateEpoch = token
            }
        } else if (!known) {
            // A final without a matching partial is the decoder guessing at end of utterance.
            return
        }

        val stable = now - candidateAt >= STABLE_SAMPLES
        if (stable && !closed && token == epoch && canAccept()) {
            fired = true
            onWake(text)
        }
    }

    fun cancel() {
        closed = true
        invalidateAudio()
    }

    private companion object {
        /** 1280 PCM16 samples at 16 kHz, the same window Trợ lý VXM uses. */
        const val STABLE_SAMPLES = 1280L
    }
}
