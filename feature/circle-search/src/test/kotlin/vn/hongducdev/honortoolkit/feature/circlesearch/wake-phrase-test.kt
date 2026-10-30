package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {
    @Test
    fun normalizesOkGoogle() {
        assertEquals("okay google", WakePhrase.normalize("OK Google"))
        assertEquals("okay google", WakePhrase.normalize("ok google"))
        assertEquals("okay google", WakePhrase.normalize("  OKAY, GOOGLE!  "))
        assertEquals("okay google", WakePhrase.normalize("Okay Google."))
    }

    @Test
    fun acceptsCommonForms() {
        assertTrue(WakePhrase.matches("OK Google"))
        assertTrue(WakePhrase.matches("ok google"))
        assertTrue(WakePhrase.matches("OKAY GOOGLE"))
        assertTrue(WakePhrase.matches("Hey Google"))
        assertTrue(WakePhrase.matches("h\u00e9y google"))
    }

    @Test
    fun rejectsOtherSpeech() {
        assertFalse(WakePhrase.matches(""))
        assertFalse(WakePhrase.matches("google"))
        assertFalse(WakePhrase.matches("okay"))
        assertFalse(WakePhrase.matches("okay google now"))
        assertFalse(WakePhrase.matches("please open google"))
    }

    @Test
    fun grammarOnlyAllowsWakeWordsAndUnknown() {
        assertEquals("""["okay google","hey google","[unk]"]""", WakePhrase.grammarJson)
    }
}
