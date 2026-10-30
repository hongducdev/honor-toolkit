package vn.hongducdev.honortoolkit.feature.circlesearch

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerLogTest {
    @Test
    fun newestLineComesFirst() {
        assertEquals("c\na\nb", TriggerLog.append("a\nb", "c", 20))
    }

    @Test
    fun keepsAtMostMaxLines() {
        assertEquals("d\nc", TriggerLog.append("c\nb\na", "d", 2))
    }

    @Test
    fun ignoresBlankLinesFromAnOlderLog() {
        assertEquals("b\na", TriggerLog.append("a\n\n", "b", 20))
    }

    @Test
    fun appendsToAnEmptyLogWithoutALeadingGap() {
        assertEquals("a", TriggerLog.append(null, "a", 20))
        assertEquals("a", TriggerLog.append("", "a", 20))
    }
}
