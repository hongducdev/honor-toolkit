package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertEquals
import org.junit.Test

class StripGesturesTest {
    @Test
    fun defaultsMatchTheReferenceApp() {
        assertEquals(StripAction.None, StripAction.defaultFor(StripGesture.Tap))
        assertEquals(StripAction.Gemini, StripAction.defaultFor(StripGesture.Double))
        assertEquals(StripAction.Circle, StripAction.defaultFor(StripGesture.Hold))
    }

    @Test
    fun unknownOrMissingKeyFallsBackToNone() {
        assertEquals(StripAction.None, StripAction.fromKey(null))
        assertEquals(StripAction.None, StripAction.fromKey("swipe_up"))
        assertEquals(StripAction.None, StripAction.fromKey(""))
    }

    @Test
    fun everyActionKeyRoundTrips() {
        StripAction.entries.forEach { assertEquals(it, StripAction.fromKey(it.key)) }
    }

    @Test
    fun gestureKeysAreStable() {
        assertEquals(listOf("tap", "double", "hold"), StripGesture.entries.map { it.key })
    }
}
