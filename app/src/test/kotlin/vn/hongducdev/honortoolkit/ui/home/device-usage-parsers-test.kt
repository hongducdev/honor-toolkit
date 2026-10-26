package vn.hongducdev.honortoolkit.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceUsageParsersTest {
    private val meminfo = """
        Applications Memory Usage (in Kilobytes):

        Total RSS by process:
            500,000K: com.rss.only (pid 1)

        Total RSS by OOM adjustment:
            500,000K: Native
                500,000K: com.rss.only (pid 1)

        Total PSS by process:
            100,000K: com.example.app (pid 10 / activities)
             20,000K: com.example.app:service (pid 11)
             50,000K: system (pid 2)

        Total PSS by OOM adjustment:
            170,000K: Native
                100,000K: com.example.app (pid 10)
    """.trimIndent()

    @Test
    fun meminfoSumsProcessesOfOnePackageFromPssTable() {
        val result = parseMeminfo(meminfo)
        assertEquals(120_000L * 1024, result["com.example.app"])
        assertEquals(50_000L * 1024, result["system"])
        assertEquals(null, result["com.rss.only"])
    }

    @Test
    fun meminfoFallsBackToRssWhenThereIsNoPssTable() {
        val rssOnly = "Total RSS by process:\n    2,048K: com.a (pid 3)\n\nTotal RSS by category:\n"
        assertEquals(2048L * 1024, parseMeminfo(rssOnly)["com.a"])
    }

    @Test
    fun meminfoWithoutTablesIsEmpty() {
        assertTrue(parseMeminfo("garbage").isEmpty())
    }

    @Test
    fun diskstatsSumsAppDataAndCacheByIndex() {
        val output = """
            Data-Free: 1K / 2K total = 67% free
            Package Names: ["com.a","com.b","com.c"]
            App Sizes: [100,200]
            App Data Sizes: [10,20,30]
            Cache Sizes: [1,2,3]
        """.trimIndent()
        val result = parseDiskstats(output)
        assertEquals(111L, result["com.a"])
        assertEquals(222L, result["com.b"])
        assertEquals(33L, result["com.c"])
    }

    @Test
    fun mergeDropsPackagesWithNoUsage() {
        val merged = mergeUsage(mapOf("a" to 5L), mapOf("b" to 7L, "c" to 0L)) { it.uppercase() }
        assertEquals(setOf("a", "b"), merged.map { it.packageName }.toSet())
        assertEquals("B", merged.first { it.packageName == "b" }.label)
    }
}
