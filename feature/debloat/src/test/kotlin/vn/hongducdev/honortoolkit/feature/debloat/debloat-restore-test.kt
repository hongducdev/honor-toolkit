package vn.hongducdev.honortoolkit.feature.debloat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebloatRestoreTest {
    @Test
    fun restoresEveryPackageInOrder() = runBlocking {
        val seen = mutableListOf<String>()
        val result = restorePackages(listOf("a.one", "b.two", "c.three")) { seen += it; DebloatOutcome.Done }
        assertEquals(RestoreResult(3), result)
        assertEquals(listOf("a.one", "b.two", "c.three"), seen)
    }

    @Test
    fun stopsAtFirstFailureAndReportsIt() = runBlocking {
        val seen = mutableListOf<String>()
        val result = restorePackages(listOf("a.one", "b.two", "c.three")) {
            seen += it
            if (it == "b.two") DebloatOutcome.Failed("boom") else DebloatOutcome.Done
        }
        assertEquals(RestoreResult(1, "b.two", "boom"), result)
        assertEquals(listOf("a.one", "b.two"), seen)
    }

    @Test
    fun emptyListRestoresNothing() = runBlocking {
        val result = restorePackages(emptyList()) { DebloatOutcome.Done }
        assertEquals(0, result.restored)
        assertNull(result.failedPackage)
    }
}
