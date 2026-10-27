package vn.hongducdev.honortoolkit.feature.gmscheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GmsCheckParsersTest {
    @Test
    fun packageNamesStripPrefix() {
        assertEquals(setOf("com.google.android.gms", "com.android.vending"), parsePackageNames("package:com.google.android.gms\npackage:com.android.vending\n"))
    }

    @Test
    fun versionNameComesFromTheRequestedPackageBlock() {
        val dump = """
            Packages:
              Package [com.google.android.gms] (2baa995):
                versionCode=264131035 minSdk=35 targetSdk=37
                versionName=26.41.31 (260400-1002748488)
              Package [com.google.android.gms] (230f724):
                versionName=25.10.36 (260400-739992411)
        """.trimIndent()
        assertEquals("26.41.31", parseVersionName(dump, "com.google.android.gms"))
        assertNull(parseVersionName(dump, "com.android.vending"))
    }

    @Test
    fun countsOnlyGoogleAccounts() {
        val dump = """
            Accounts: 3
              Account {name=a@example.com, type=com.google}
              Account {name=b, type=com.facebook.auth.login}
              Account {name=c@example.com, type=com.google}
        """.trimIndent()
        assertEquals(2, countGoogleAccounts(dump))
        assertEquals(0, countGoogleAccounts("Accounts: 0"))
    }
}
