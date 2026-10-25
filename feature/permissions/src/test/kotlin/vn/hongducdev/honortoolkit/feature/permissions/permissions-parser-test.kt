package vn.hongducdev.honortoolkit.feature.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Trimmed from `dumpsys package` and `cmd appops get` on a PTP-AN10 (Android SDK 37).
private val DUMP = """
Packages:
  Package [com.convx.music] (abc):
    install permissions:
      android.permission.INTERNET: granted=true
    User 0: ceDataInode=505396 installed=true hidden=false
      installReason=4
      gids=[3003]
      runtime permissions:
        android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
        android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|SYSTEM_FIXED]
        android.permission.READ_MEDIA_AUDIO: granted=false, flags=[ ]
      enabledComponents:
        androidx.work.impl.background.systemjob.SystemJobService
    User 128: ceDataInode=0 installed=false hidden=false
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ ]
""".trimIndent()

private val APPOPS = """
Uid mode: COARSE_LOCATION: ignore
FINE_LOCATION: ignore
WAKE_LOCK: allow; time=+1d2h ago
RUN_IN_BACKGROUND: allow (default)
COARSE_LOCATION: allow
""".trimIndent()

class PermissionsParserTest {
    @Test
    fun readsRuntimePermissionsOfUserZeroOnly() {
        val dump = parsePackageDump(DUMP)
        assertEquals(
            listOf("android.permission.POST_NOTIFICATIONS", "android.permission.RECORD_AUDIO", "android.permission.READ_MEDIA_AUDIO"),
            dump.permissions.map { it.name },
        )
        assertEquals(listOf(true, false, false), dump.permissions.map { it.granted })
        assertFalse(dump.sharedUid)
    }

    @Test
    fun systemFixedFlagMakesPermissionFixed() {
        val byName = parsePackageDump(DUMP).permissions.associateBy { it.name }
        assertTrue(byName.getValue("android.permission.RECORD_AUDIO").fixed)
        assertFalse(byName.getValue("android.permission.POST_NOTIFICATIONS").fixed)
        assertTrue(byName.getValue("android.permission.READ_MEDIA_AUDIO").flags.isEmpty())
    }

    @Test
    fun missingUserBlockGivesNoPermissions() {
        assertTrue(parsePackageDump("Packages:\n  Package [x]:\n").permissions.isEmpty())
    }

    @Test
    fun sharedUserIsDetected() {
        assertTrue(parsePackageDump("    sharedUser=SharedUserSetting{1 android.uid.shared/1000}\n    User 0: x\n").sharedUid)
        assertFalse(parsePackageDump("    sharedUser=null\n").sharedUid)
    }

    @Test
    fun appOpsKeepModeAndUidModeWins() {
        val ops = parseAppOps(APPOPS).associate { it.name to it.mode }
        assertEquals(AppOpMode.Ignore, ops["COARSE_LOCATION"])
        assertEquals(AppOpMode.Allow, ops["WAKE_LOCK"])
        assertEquals(AppOpMode.Allow, ops["RUN_IN_BACKGROUND"])
        assertEquals(4, ops.size)
    }

    @Test
    fun noOperationsGivesEmptyList() {
        assertTrue(parseAppOps("No operations.").isEmpty())
    }
}

class PermissionGuardTest {
    private val fixed = PermissionEntry("android.permission.RECORD_AUDIO", false, setOf("POLICY_FIXED"))
    private val free = PermissionEntry("android.permission.CAMERA", false, emptySet())

    @Test
    fun appMustBeUserAppWithOwnUid() {
        val user = setOf("com.example.app")
        assertNull(PermissionGuard.checkApp("com.example.app", user, sharedUid = false))
        assertEquals(BlockReason.NotUserApp, PermissionGuard.checkApp("com.android.settings", user, false))
        assertEquals(BlockReason.SharedUid, PermissionGuard.checkApp("com.example.app", user, true))
        assertEquals(BlockReason.InvalidName, PermissionGuard.checkApp("com.x; id", user, false))
    }

    @Test
    fun fixedAndUnknownPermissionsAreBlocked() {
        assertEquals(BlockReason.FixedBySystem, PermissionGuard.checkPermission(fixed.name, fixed))
        assertEquals(BlockReason.NotChangeable, PermissionGuard.checkPermission("android.permission.INTERNET", null))
        assertNull(PermissionGuard.checkPermission(free.name, free))
        assertEquals(BlockReason.InvalidName, PermissionGuard.checkPermission("a b", free))
    }

    @Test
    fun appOpNameRejectsShellMetacharacters() {
        assertNull(PermissionGuard.checkAppOp("RUN_IN_BACKGROUND"))
        assertEquals(BlockReason.InvalidName, PermissionGuard.checkAppOp("WAKE_LOCK; id"))
        assertEquals(BlockReason.InvalidName, PermissionGuard.checkAppOp("wake_lock"))
    }
}
