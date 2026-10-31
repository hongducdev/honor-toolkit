package vn.hongducdev.honortoolkit.feature.tune

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.hongducdev.honortoolkit.core.adb.AdbShell
import vn.hongducdev.honortoolkit.core.adb.ShellResult

class TuneControllerTest {

    private class FakeShell(private val store: MutableMap<String, String>) : AdbShell {
        val commands = mutableListOf<String>()

        override suspend fun run(command: String): ShellResult {
            commands += command
            val output = StringBuilder()
            for (part in command.split("; ")) {
                val put = Regex("settings put ([a-z]+) ([^ ]+) ([^ ]+)").matchEntire(part)
                val get = Regex("echo -n '__HT_([^=]+)=' ?").find(part)
                when {
                    put != null -> store["${put.groupValues[1]}/${put.groupValues[2]}"] = put.groupValues[3]
                    get != null -> output.append("__HT_${get.groupValues[1]}=${store[get.groupValues[1]] ?: "null"}\n")
                }
            }
            return ShellResult(0, output.toString())
        }
    }

    @Test
    fun snapshotIgnoresVendorKeysTheRomDoesNotHave() = runBlocking {
        val shell = FakeShell(mutableMapOf("global/heads_up_notifications_enabled" to "1"))

        val snapshot = TuneController(shell).snapshot()!!

        assertEquals(true, snapshot.toggles[TuneId.HeadsUp])
        assertNull(snapshot.toggles[TuneId.HiBoard])
    }

    @Test
    fun unsupportedRowWritesNothing() = runBlocking {
        val shell = FakeShell(mutableMapOf())

        val outcome = TuneController(shell).setToggle(TuneCatalog.toggle(TuneId.HiBoard), true)

        assertEquals(TuneOutcome.Unsupported, outcome)
        assertTrue(shell.commands.none { it.contains("settings put") })
    }

    @Test
    fun supportedRowIsWrittenAndReadBack() = runBlocking {
        val store = mutableMapOf("secure/hnnotification_classification_state" to "1")

        val outcome = TuneController(FakeShell(store)).setToggle(TuneCatalog.toggle(TuneId.GentleNotifications), true)

        assertEquals(TuneOutcome.Applied, outcome)
        assertEquals("0", store["secure/hnnotification_classification_state"])
    }
}
