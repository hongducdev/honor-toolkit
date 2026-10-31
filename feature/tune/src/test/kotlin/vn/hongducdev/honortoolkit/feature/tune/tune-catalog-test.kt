package vn.hongducdev.honortoolkit.feature.tune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneCatalogTest {
    private fun readings(vararg pairs: Pair<SettingKey, String?>): Map<SettingKey, String?> = pairs.toMap()

    private fun key(namespace: TuneNamespace, name: String) = SettingKey(namespace, name)

    @Test
    fun everyControlIdAppearsExactlyOnce() {
        val ids = (TuneCatalog.toggles.map { it.id } + TuneCatalog.choices.map { it.id })
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(TuneId.entries.size, ids.size)
    }

    @Test
    fun recommendedIdsExist() {
        val known = (TuneCatalog.toggles.map { it.id } + TuneCatalog.choices.map { it.id }).toSet()
        assertTrue(TuneCatalog.recommended.all { it in known })
        assertTrue(TuneCatalog.recommendedChoices.keys.all { it in known })
    }

    @Test
    fun readKeysAreNotDuplicated() {
        val keys = TuneCatalog.allReadKeys
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun toggleIsOnOnlyWhenEveryKeyMatches() {
        val hiBoard = TuneCatalog.toggle(TuneId.HiBoard)
        val bothOff = readings(
            key(TuneNamespace.Secure, "hiboard_switch") to "0",
            key(TuneNamespace.System, "hiboard_switch") to "0",
        )
        val halfOn = readings(
            key(TuneNamespace.Secure, "hiboard_switch") to "0",
            key(TuneNamespace.System, "hiboard_switch") to "1",
        )

        assertTrue(hiBoard.matches(true, bothOff))
        assertFalse(hiBoard.matches(false, bothOff))
        assertFalse(hiBoard.matches(true, halfOn))
        assertFalse(hiBoard.matches(false, halfOn))
    }

    @Test
    fun jobBatchingIsOffWhenTheRomHasNoValue() {
        val spec = TuneCatalog.toggle(TuneId.JobBatching)
        val constants = "min_ready_non_active_jobs_count=1,max_non_active_job_batch_delay_ms=0"

        assertTrue(spec.matches(true, readings(key(TuneNamespace.Global, "job_scheduler_constants") to constants)))
        assertTrue(spec.matches(false, readings(key(TuneNamespace.Global, "job_scheduler_constants") to null)))
        assertTrue(spec.matches(false, readings(key(TuneNamespace.Global, "job_scheduler_constants") to "other")))
    }

    @Test
    fun privateDnsIsOffWhenTheModeIsNotHostname() {
        val spec = TuneCatalog.toggle(TuneId.PrivateDnsAdBlock)

        assertTrue(
            spec.matches(
                true,
                readings(
                    key(TuneNamespace.Global, "private_dns_mode") to "hostname",
                    key(TuneNamespace.Global, "private_dns_specifier") to "dns.adguard-dns.com",
                ),
            ),
        )
        assertTrue(spec.matches(false, readings(key(TuneNamespace.Global, "private_dns_mode") to "opportunistic")))
        assertFalse(
            spec.matches(
                false,
                readings(
                    key(TuneNamespace.Global, "private_dns_mode") to "hostname",
                    key(TuneNamespace.Global, "private_dns_specifier") to "dns.adguard-dns.com",
                ),
            ),
        )
    }

    @Test
    fun suplIsOffWhenTheHostWasDeleted() {
        val spec = TuneCatalog.toggle(TuneId.SuplGps)

        assertTrue(
            spec.matches(
                true,
                readings(
                    key(TuneNamespace.Global, "supl_host") to "supl.google.com",
                    key(TuneNamespace.Global, "supl_port") to "7275",
                ),
            ),
        )
        assertTrue(spec.matches(false, readings(key(TuneNamespace.Global, "supl_host") to null)))
    }

    @Test
    fun callerIdOnlyCountsWhenBothNamespacesHoldTheValue() {
        val spec = TuneCatalog.toggle(TuneId.CallerIdMatch)

        assertTrue(
            spec.matches(
                true,
                readings(
                    key(TuneNamespace.System, "min_match") to "7",
                    key(TuneNamespace.Global, "min_match") to "7",
                ),
            ),
        )
        assertTrue(
            spec.matches(
                false,
                readings(
                    key(TuneNamespace.System, "min_match") to null,
                    key(TuneNamespace.Global, "min_match") to null,
                ),
            ),
        )
    }

    @Test
    fun commandsAreSingleLineSettingsOrCmd() {
        val commands = TuneCatalog.toggles.flatMap {
            it.commands(true) + it.commands(false)
        } + TuneCatalog.choices.flatMap { choice -> choice.options.flatMap { it.commands } } +
            TuneCatalog.restoreCommands + TuneCatalog.gpsResetCommands

        assertTrue(commands.isNotEmpty())
        commands.forEach { command ->
            assertFalse(command, command.contains('\n'))
            assertTrue(command, command.startsWith("settings ") || command.startsWith("cmd location "))
        }
    }

    @Test
    fun everyToggleWritesOrDeletesSomething() {
        TuneCatalog.toggles.forEach { spec ->
            assertTrue(spec.id.name, spec.commands(true).isNotEmpty())
            assertTrue(spec.id.name, spec.commands(false).isNotEmpty())
            assertTrue(spec.id.name, spec.readKeys.isNotEmpty())
        }
    }

    @Test
    fun refreshChoiceWritesTheMagicOsMode() {
        val spec = TuneCatalog.choice(TuneId.RefreshRate)

        assertEquals(listOf("0", "1", "2"), spec.options.map { it.value })
        assertEquals("secure/hw_screen_freq", spec.readKey.path)
        assertEquals(listOf("settings put secure hw_screen_freq 0"), spec.option("0")!!.commands)
        assertEquals("2", spec.selectionOf("2"))
        assertNull(spec.selectionOf("7"))
    }

    @Test
    fun restoreDeletesEveryKeyOnce() {
        val keys = TuneCatalog.restoreKeys

        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.contains(SettingKey(TuneNamespace.Secure, "hw_screen_freq")))
        assertTrue(keys.contains(SettingKey(TuneNamespace.Global, "animator_duration_scale")))
        TuneCatalog.restoreCommands.forEach { assertTrue(it, it.startsWith("settings delete ")) }
    }

    @Test
    fun restoreLeavesUserOwnedKeysAlone() {
        val keys = TuneCatalog.restoreKeys

        assertFalse(keys.contains(SettingKey(TuneNamespace.Global, "private_dns_mode")))
        assertFalse(keys.contains(SettingKey(TuneNamespace.Global, "private_dns_specifier")))
        assertFalse(keys.contains(SettingKey(TuneNamespace.Secure, "limit_ad_tracking")))
    }

    @Test
    fun captivePortalOffDeletesInsteadOfDisablingDetection() {
        val commands = TuneCatalog.toggle(TuneId.CaptivePortal).commands(false)

        assertTrue(commands.contains("settings delete global captive_portal_mode"))
        assertTrue(commands.none { it.contains("captive_portal_mode 0") })
    }

    @Test
    fun environmentDependentRowsAreNotRecommended() {
        assertFalse(TuneId.CaptivePortal in TuneCatalog.recommended)
        assertFalse(TuneId.SuplGps in TuneCatalog.recommended)
    }

    @Test
    fun rowWithNoVendorKeyOnThisRomIsUnsupported() {
        val spec = TuneCatalog.toggle(TuneId.HiBoard)

        assertNull(spec.supportedBy(emptySet()))
    }

    @Test
    fun supportedRowOnlyWritesAndChecksKeysThatExist() {
        val spec = TuneCatalog.toggle(TuneId.HeadsUp)
        val present = setOf(SettingKey(TuneNamespace.Global, "heads_up_notifications_enabled"))

        val effective = spec.supportedBy(present)!!

        assertEquals(listOf("settings put global heads_up_notifications_enabled 1"), effective.commands(true))
        assertTrue(effective.matches(true, mapOf(present.first() to "1")))
    }

    @Test
    fun aospKeyRowsPassThroughWithoutVendorKeys() {
        assertEquals(TuneCatalog.toggle(TuneId.PrivateDnsAdBlock), TuneCatalog.toggle(TuneId.PrivateDnsAdBlock).supportedBy(emptySet()))
    }
}
