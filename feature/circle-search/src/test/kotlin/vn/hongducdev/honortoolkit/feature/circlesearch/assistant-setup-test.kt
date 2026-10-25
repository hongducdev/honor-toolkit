package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSetupTest {
    @Test
    fun parsesSeparatedHolders() {
        assertEquals(listOf("a.b", "c.d.e"), parseRoleHolders("a.b;c.d.e\n"))
    }

    @Test
    fun dropsNoiseAndInjection() {
        assertEquals(emptyList<String>(), parseRoleHolders("Error: no role; \$(rm -rf x)"))
    }
}
