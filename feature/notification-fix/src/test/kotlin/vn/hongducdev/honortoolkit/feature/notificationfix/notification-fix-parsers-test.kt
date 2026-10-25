package vn.hongducdev.honortoolkit.feature.notificationfix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFixParsersTest {
    @Test
    fun notificationGrantedReadsRuntimeLine() {
        val dump = "      runtime permissions:\n        android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SET]"
        assertEquals(false, parseNotificationGranted(dump))
        assertEquals(true, parseNotificationGranted(dump.replace("false", "true")))
    }

    @Test
    fun notificationGrantedIsNullWhenNotRuntimePermission() {
        assertNull(parseNotificationGranted("android.permission.INTERNET: granted=true"))
    }

    @Test
    fun dozeWhitelistMatchesExactPackageOnly() {
        val output = "system-excidle,com.android.vending,10165\nuser,com.zing.zalo,10300"
        assertTrue(isDozeWhitelisted(output, "com.zing.zalo"))
        assertFalse(isDozeWhitelisted(output, "com.zing"))
        assertFalse(isDozeWhitelisted(output, "10165"))
    }

    @Test
    fun appOpAllowedNeedsExplicitAllow() {
        assertEquals(true, parseAppOpAllowed("RUN_ANY_IN_BACKGROUND: allow"))
        assertEquals(false, parseAppOpAllowed("RUN_ANY_IN_BACKGROUND: ignore; time=+1m ago"))
        assertEquals(false, parseAppOpAllowed("RUN_ANY_IN_BACKGROUND: default"))
        assertNull(parseAppOpAllowed("No operations."))
    }

    @Test
    fun appOpUidModeWinsUnlessDefault() {
        assertEquals(false, parseAppOpAllowed("Uid mode: SCHEDULE_EXACT_ALARM: ignore\nSCHEDULE_EXACT_ALARM: allow"))
        assertEquals(true, parseAppOpAllowed("Uid mode: SCHEDULE_EXACT_ALARM: default\nSCHEDULE_EXACT_ALARM: allow"))
    }

    @Test
    fun packageNameValidationRejectsShellMetacharacters() {
        assertTrue(isValidPackageName("com.zing.zalo"))
        assertFalse(isValidPackageName("com.zing; reboot"))
        assertFalse(isValidPackageName("com.zing.zalo$(id)"))
    }
}
