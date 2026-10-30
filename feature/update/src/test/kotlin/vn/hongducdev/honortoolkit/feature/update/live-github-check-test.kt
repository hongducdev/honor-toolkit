package vn.hongducdev.honortoolkit.feature.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Manual smoke test against the real repository. Skipped unless `HONOR_UPDATE_LIVE=1`, so CI and
 * normal builds stay offline:
 *
 * ```
 * HONOR_UPDATE_LIVE=1 ./gradlew :feature:update:testDebugUnitTest --tests "*LiveGitHubCheckTest*"
 * ```
 */
class LiveGitHubCheckTest {

    @Test
    fun readsAndDownloadsTheLatestRelease() {
        assumeTrue("Set HONOR_UPDATE_LIVE=1 to call GitHub", System.getenv("HONOR_UPDATE_LIVE") == "1")
        val client = GitHubReleaseClient(BuildConfig.GITHUB_REPO)

        val release = client.latest()
        val apk = release.apk
        assertNotNull("Latest release must carry an APK", apk)
        assertTrue("Tag should parse as a version", release.version.parts.isNotEmpty())
        assertTrue("The installer needs the digest to verify the download", apk!!.digest?.startsWith("sha256:") == true)

        val target = File(System.getProperty("java.io.tmpdir"), apk.name)
        try {
            client.download(apk, target) {}
            assertEquals(apk.size, target.length())
        } finally {
            target.delete()
        }
    }
}
