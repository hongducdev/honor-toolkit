package vn.hongducdev.honortoolkit.feature.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTextTest {
    @Test
    fun stripsHeadingsEmphasisAndLinks() {
        val md = "## Mới\n\n- **Sửa** lỗi `ADB`\n* Xem [ghi chú](https://example.com/x)\n\n\n\nHết"
        assertEquals("Mới\n\n• Sửa lỗi ADB\n• Xem ghi chú\n\nHết", releaseNotesToPlainText(md))
    }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("Không đổi", releaseNotesToPlainText("Không đổi"))
    }
}
