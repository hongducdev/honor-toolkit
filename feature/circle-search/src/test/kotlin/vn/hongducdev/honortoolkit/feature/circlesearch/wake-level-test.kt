package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeLevelTest {
    @Test
    fun loudAudioPassesWhileFresh() {
        val level = WakeLevel()
        val loud = ShortArray(1280) { Short.MAX_VALUE }
        level.add(loud, loud.size, 1000L)
        assertTrue(level.allows(80, 1000L))
    }

    @Test
    fun silenceAndStaleAudioDoNotPass() {
        val level = WakeLevel()
        level.add(ShortArray(1280), 1280, 1000L)
        assertFalse(level.allows(80, 1000L))

        val loud = ShortArray(1280) { Short.MAX_VALUE }
        level.add(loud, loud.size, 2000L)
        assertFalse(level.allows(80, 3500L))
    }
}
