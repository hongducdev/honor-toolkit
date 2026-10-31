package vn.hongducdev.honortoolkit.feature.debloat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebloatSuggestionsTest {
    @Test
    fun everySuggestedPackageIsAPlainPackageName() {
        DebloatSuggestions.entries.keys.forEach { assertTrue(it, isValidPackageName(it)) }
    }

    @Test
    fun adviceNeverTargetsCoreSystemPackages() {
        DebloatSuggestions.entries.keys.forEach {
            assertNull(it, DebloatDenylist.protectionOf(it, emptySet()))
        }
    }

    @Test
    fun knownChinaPackagesCarryAdvice() {
        listOf("com.hihonor.hiboard", "com.hihonor.search", "com.hihonor.video", "com.hihonor.powergenius")
            .forEach { assertTrue(it, DebloatSuggestions.of(it) != null) }
        assertNull(DebloatSuggestions.of("com.example.note"))
    }

    @Test
    fun suggestionsHaveReasons() {
        DebloatSuggestions.entries.values.forEach { assertFalse(it.reason == 0) }
    }
}
