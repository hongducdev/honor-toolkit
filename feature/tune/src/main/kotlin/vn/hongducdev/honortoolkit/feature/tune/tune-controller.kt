package vn.hongducdev.honortoolkit.feature.tune

import vn.hongducdev.honortoolkit.core.adb.AdbShell

internal enum class TuneOutcome {
    /** Written and read back with the expected value. */
    Applied,

    /** The command ran but the ROM kept its old value. */
    Refused,

    /** The shell itself did not answer. */
    ShellFailed,

    /** This ROM has none of the keys the row writes, so nothing was changed. */
    Unsupported,
}

internal data class TuneSnapshot(
    val toggles: Map<TuneId, Boolean?>,
    val choices: Map<TuneId, String?>,
)

internal data class PresetOutcome(val applied: Int, val total: Int, val skipped: Int = 0)

/**
 * Applies a fixed catalogue of MagicOS settings and reads every one back. Nothing here trusts a
 * command's exit code: a ROM that silently ignores `settings put` is reported as [TuneOutcome.Refused],
 * which is the same rule the debloat and notification modules follow.
 */
internal class TuneController(private val shell: AdbShell) {

    /** Null when the shell could not answer; individual controls are null when the ROM has no such key. */
    suspend fun snapshot(): TuneSnapshot? {
        val readings = read(TuneCatalog.allReadKeys) ?: return null
        return TuneSnapshot(
            toggles = TuneCatalog.toggles.associate { it.id to resolve(it, readings) },
            choices = TuneCatalog.choices.associate { it.id to readings[it.readKey] },
        )
    }

    suspend fun setToggle(spec: ToggleSpec, enabled: Boolean): TuneOutcome {
        val present = presentKeys(spec.vendorKeys) ?: return TuneOutcome.ShellFailed
        val effective = spec.supportedBy(present) ?: return TuneOutcome.Unsupported
        runCommands(effective.commands(enabled))
        val readings = read(effective.readKeys) ?: return TuneOutcome.ShellFailed
        return if (effective.matches(enabled, readings)) TuneOutcome.Applied else TuneOutcome.Refused
    }

    suspend fun choose(spec: ChoiceSpec, value: String): TuneOutcome {
        val option = spec.option(value) ?: return TuneOutcome.Refused
        val present = presentKeys(listOf(spec.readKey)) ?: return TuneOutcome.ShellFailed
        if (spec.readKey !in present) return TuneOutcome.Unsupported
        runCommands(option.commands)
        val readings = read(listOf(spec.readKey)) ?: return TuneOutcome.ShellFailed
        return if (spec.matches(option.value, readings[spec.readKey])) TuneOutcome.Applied else TuneOutcome.Refused
    }

    /** Writes every recommended row this ROM supports, then reports how many it kept and how many it skipped. */
    suspend fun applyRecommended(): PresetOutcome? {
        val wanted = TuneCatalog.toggles.filter { it.id in TuneCatalog.recommended }
        val choices = TuneCatalog.recommendedChoices.map { (id, value) -> TuneCatalog.choice(id) to value }
        val present = presentKeys(wanted.flatMap { it.vendorKeys } + choices.map { it.first.readKey }) ?: return null

        val toggles = wanted.mapNotNull { it.supportedBy(present) }
        val supportedChoices = choices.filter { it.first.readKey in present }
        toggles.forEach { runCommands(it.commands(true)) }
        supportedChoices.forEach { (spec, value) -> spec.option(value)?.let { runCommands(it.commands) } }

        val readings = read((toggles.flatMap { it.readKeys } + supportedChoices.map { it.first.readKey }).distinct()) ?: return null
        val applied = toggles.count { it.matches(true, readings) } +
            supportedChoices.count { (spec, value) -> spec.matches(value, readings[spec.readKey]) }
        val total = toggles.size + supportedChoices.size
        return PresetOutcome(applied, total, skipped = wanted.size + choices.size - total)
    }

    /** Deletes every key this page can write and reports how many are really gone. */
    suspend fun restoreDefaults(): PresetOutcome? {
        runCommands(TuneCatalog.restoreCommands)
        val readings = read(TuneCatalog.restoreKeys) ?: return null
        return PresetOutcome(readings.count { it.value == null }, readings.size)
    }

    /** One-shot GPS refresh. True when at least one command was accepted; there is nothing to read back. */
    suspend fun resetGps(): Boolean = TuneCatalog.gpsResetCommands.any { shell.run(it).isSuccess }

    private suspend fun presentKeys(keys: List<SettingKey>): Set<SettingKey>? =
        read(keys.distinct())?.filterValues { it != null }?.keys

    private fun resolve(spec: ToggleSpec, readings: Map<SettingKey, String?>): Boolean? {
        val present = readings.filterValues { it != null }.keys
        val effective = spec.supportedBy(present) ?: return null
        return when {
            effective.matches(true, readings) -> true
            effective.matches(false, readings) -> false
            else -> null
        }
    }

    private suspend fun read(keys: List<SettingKey>): Map<SettingKey, String?>? {
        if (keys.isEmpty()) return emptyMap()
        val command = keys.joinToString("; ") {
            "echo -n '$SETTINGS_MARKER${it.path}='; settings get ${it.command} 2>/dev/null"
        }
        val result = shell.run(command)
        if (!result.isSuccess) return null
        val parsed = parseSettingsDump(result.output)
        return keys.associateWith { parsed[it] }
    }

    private suspend fun runCommands(commands: List<String>) {
        if (commands.isEmpty()) return
        // `;` keeps going after a refused write, and one round trip is much faster than one per key.
        shell.run(commands.joinToString("; "))
    }
}
