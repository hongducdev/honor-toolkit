package vn.hongducdev.honortoolkit.feature.debloat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebloatGuardTest {
    @Test
    fun coreSystemPackagesAreProtected() {
        listOf("androidhnext", "com.hihonor.systemserver", "com.qualcomm.qti.ims", "com.android.systemui","com.android.settings", "com.google.android.gms", "com.android.providers.media")
            .forEach { assertEquals(it, ProtectionReason.CoreSystem, DebloatDenylist.protectionOf(it, emptySet())) }
    }

    @Test
    fun packagesInUseAreProtected() {
        assertEquals(ProtectionReason.InUse, DebloatDenylist.protectionOf("com.example.keyboard", setOf("com.example.keyboard")))
    }

    @Test
    fun unrelatedPackageIsAllowed() {
        assertNull(DebloatDenylist.protectionOf("com.example.bloat", setOf("com.example.keyboard")))
    }

    @Test
    fun packageNameRejectsShellMetacharacters() {
        assertTrue(isValidPackageName("com.hihonor.android.launcher"))
        assertFalse(isValidPackageName("com.x; rm -rf /"))
        assertFalse(isValidPackageName("com.x && id"))
        assertFalse(isValidPackageName(""))
        assertFalse(isValidPackageName("com.x\nid"))
    }
}
