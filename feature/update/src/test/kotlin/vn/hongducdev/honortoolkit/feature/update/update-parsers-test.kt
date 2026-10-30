package vn.hongducdev.honortoolkit.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun parsesTagWithLeadingV() {
        assertEquals("0.2.1", AppVersion.parse("v0.2.1").toString())
    }

    @Test
    fun rejectsTagsThatAreNotNumericVersions() {
        assertNull(AppVersion.parse("nightly"))
        assertNull(AppVersion.parse("v1.x.0"))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse(null))
    }

    @Test
    fun comparesPartsNumerically() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
    }

    @Test
    fun treatesMissingPartsAsZero() {
        assertEquals(0, AppVersion.parse("1.2")!!.compareTo(AppVersion.parse("1.2.0")!!))
    }

    @Test
    fun ranksPrereleaseBelowFinal() {
        assertTrue(AppVersion.parse("1.0.0-beta.1")!! < AppVersion.parse("1.0.0")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("1.0.0-rc.1")!!)
    }
}

class LatestReleaseTest {

    @Test
    fun picksTheNamedApkAndKeepsTheDigest() {
        val release = parseLatestRelease(Payload)

        assertEquals("v0.2.0", release.tag)
        assertEquals("0.2.0", release.version.toString())
        assertEquals("Bản 0.2.0", release.title)
        assertEquals("https://github.com/hongducdev/honor-toolkit/releases/tag/v0.2.0", release.pageUrl)
        assertEquals("honor-toolkit-v0.2.0.apk", release.apk?.name)
        assertEquals(12_345_678L, release.apk?.size)
        assertEquals("sha256:abc123", release.apk?.digest)
    }

    @Test
    fun ignoresNonApkAssets() {
        val release = parseLatestRelease(Payload)
        assertTrue(release.apk?.url?.endsWith("honor-toolkit-v0.2.0.apk") == true)
    }

    @Test
    fun skipsDebugApkAttachedToTheSameRelease() {
        val release = parseLatestRelease(Payload)
        assertTrue(release.apk?.name?.contains("debug") == false)
    }

    @Test
    fun failsWhenTheTagIsNotAVersion() {
        assertThrows(UpdateException::class.java) {
            parseLatestRelease("""{"tag_name":"nightly","assets":[]}""")
        }
    }
}

private const val Payload = """
{
  "tag_name": "v0.2.0",
  "name": "Bản 0.2.0",
  "body": "Sửa lỗi và thêm cập nhật trong ứng dụng.",
  "html_url": "https://github.com/hongducdev/honor-toolkit/releases/tag/v0.2.0",
  "published_at": "2026-11-01T09:30:00Z",
  "assets": [
    {
      "name": "checksums.txt",
      "browser_download_url": "https://example.com/checksums.txt",
      "size": 120,
      "digest": null
    },
    {
      "name": "honor-toolkit-v0.2.0-debug.apk",
      "browser_download_url": "https://example.com/honor-toolkit-v0.2.0-debug.apk",
      "size": 500,
      "digest": null
    },
    {
      "name": "honor-toolkit-v0.2.0.apk",
      "browser_download_url": "https://example.com/honor-toolkit-v0.2.0.apk",
      "size": 12345678,
      "digest": "sha256:abc123"
    }
  ]
}
"""
