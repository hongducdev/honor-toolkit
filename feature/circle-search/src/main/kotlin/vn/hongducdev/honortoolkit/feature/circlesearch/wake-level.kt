package vn.hongducdev.honortoolkit.feature.circlesearch

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rolling loudness gate from Trợ lý VXM's `WakeLevel`. It rejects quiet recognitions (a TV in
 * another room, a pocket, a faint echo) so the recognizer cannot fire on its own hallucinations.
 */
internal class WakeLevel {
    private val levels = DoubleArray(SIZE)
    private val times = LongArray(SIZE)
    private var count = 0
    private var next = 0

    @Synchronized
    fun add(audio: ShortArray, length: Int, now: Long) {
        if (length <= 0) return
        var energy = 0.0
        for (i in 0 until length) {
            val sample = audio[i].toDouble()
            energy += sample * sample
        }
        levels[next] = log10(max(1.0, sqrt(energy / length)) / 32768.0) * 20.0
        times[next] = now
        next = (next + 1) % SIZE
        count = min(count + 1, SIZE)
    }

    @Synchronized
    fun allows(sensitivity: Int, now: Long): Boolean {
        val threshold = -25.0 - sensitivity * 0.4
        for (i in 0 until count) {
            val age = now - times[i]
            if (age in 0..1000 && levels[i] >= threshold) return true
        }
        return false
    }

    @Synchronized
    fun display(now: Long): IntArray {
        val out = IntArray(SIZE)
        if (count == 0 || now - times[(next + SIZE - 1) % SIZE] > 240) return out
        for (i in 0 until count) {
            val index = (next - count + i + SIZE) % SIZE
            if (now - times[index] <= 1280) {
                out[SIZE - count + i] = ((levels[index] + 60.0) * 2.5).roundToInt().coerceIn(0, 100)
            }
        }
        return out
    }

    private companion object {
        const val SIZE = 16
    }
}
