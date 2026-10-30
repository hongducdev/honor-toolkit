package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeFilterTest {
    private var canAccept = true
    private var levelAllowed = true
    private val wakes = mutableListOf<String>()

    private fun filter() = WakeFilter(
        canAccept = { canAccept },
        levelAllowed = { levelAllowed },
        onWake = { wakes.add(it) },
    )

    @Test
    fun partialMustStayStableForOneBuffer() {
        val filter = filter()
        filter.candidate("""{"partial":"okay google"}""", partial = true)
        assertTrue(wakes.isEmpty())

        filter.push(ShortArray(1280), 1280)
        filter.candidate("""{"partial":"okay google"}""", partial = true)
        assertEquals(listOf("okay google"), wakes)
    }

    @Test
    fun finalResultFiresImmediately() {
        filter().candidate("""{"text":"okay google"}""", partial = false)
        assertEquals(listOf("okay google"), wakes)
    }

    @Test
    fun quietAudioBlocksWake() {
        levelAllowed = false
        filter().candidate("""{"text":"okay google"}""", partial = false)
        assertTrue(wakes.isEmpty())
    }

    @Test
    fun otherTextDoesNotWake() {
        filter().candidate("""{"text":"open google"}""", partial = false)
        assertTrue(wakes.isEmpty())
    }

    @Test
    fun firesOnlyOnce() {
        val filter = filter()
        filter.candidate("""{"text":"okay google"}""", partial = false)
        filter.candidate("""{"text":"okay google"}""", partial = false)
        assertEquals(1, wakes.size)
    }

    @Test
    fun audioInvalidationRestartsStabilityWindow() {
        val filter = filter()
        filter.candidate("""{"partial":"okay google"}""", partial = true)
        filter.push(ShortArray(1280), 1280)
        filter.invalidateAudio()
        filter.candidate("""{"partial":"okay google"}""", partial = true)
        assertTrue(wakes.isEmpty())

        filter.push(ShortArray(1280), 1280)
        filter.candidate("""{"partial":"okay google"}""", partial = true)
        assertEquals(1, wakes.size)
    }

    @Test
    fun disabledServiceDoesNotWake() {
        canAccept = false
        filter().candidate("""{"text":"okay google"}""", partial = false)
        assertTrue(wakes.isEmpty())
    }
}
