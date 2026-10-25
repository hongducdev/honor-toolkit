package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import vn.hongducdev.honortoolkit.core.adb.AdbShell

sealed interface AssistantResult {
    data object Done : AssistantResult
    data class Failed(val reason: String) : AssistantResult
}

private val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

internal fun parseRoleHolders(output: String): List<String> =
    output.split(Regex("[;\\s]+")).filter { PACKAGE_PATTERN.matches(it) }

/**
 * Makes Google the default assistant through the shell. The previous holder is stored first so
 * [restore] can put it back.
 */
class AssistantSetup(context: Context, private val shell: AdbShell) {
    private val prefs = context.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    val hasPrevious: Boolean get() = prefs.getString(KEY_PREVIOUS, null) != null

    suspend fun setGoogle(): AssistantResult {
        val before = holders() ?: return AssistantResult.Failed("role lookup")
        if (GOOGLE_APP !in before) {
            before.firstOrNull()?.let { prefs.edit().putString(KEY_PREVIOUS, it).apply() }
            shell.run("cmd role add-role-holder --user 0 $ASSISTANT_ROLE $GOOGLE_APP")
        }
        if (GOOGLE_APP in (holders() ?: emptyList()) && secureAssistantIsGoogle()) return AssistantResult.Done

        // The role alone is not enough on some builds (the trigger reads the secure setting), so set both keys directly.
        for (key in listOf("assistant", "voice_interaction_service")) {
            val put = shell.run("settings put secure $key $GOOGLE_SERVICE")
            if (!put.isSuccess) return AssistantResult.Failed(put.output.trim().ifEmpty { "settings put $key" })
        }
        return if (secureAssistantIsGoogle()) AssistantResult.Done else AssistantResult.Failed("assistant not applied")
    }

    suspend fun restore(): AssistantResult? {
        val previous = prefs.getString(KEY_PREVIOUS, null)?.takeIf { PACKAGE_PATTERN.matches(it) } ?: return null
        val result = shell.run("cmd role add-role-holder --user 0 $ASSISTANT_ROLE $previous")
        if (!result.isSuccess) return AssistantResult.Failed(result.output.trim().ifEmpty { "add-role-holder" })
        prefs.edit().remove(KEY_PREVIOUS).apply()
        return AssistantResult.Done
    }

    private suspend fun secureAssistantIsGoogle(): Boolean {
        val result = shell.run("settings get secure assistant")
        return result.isSuccess && result.output.trim().startsWith(GOOGLE_APP)
    }

    private suspend fun holders(): List<String>? {
        val result = shell.run("cmd role get-role-holders --user 0 $ASSISTANT_ROLE")
        return if (result.isSuccess) parseRoleHolders(result.output) else null
    }

    companion object {
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_SERVICE = "$GOOGLE_APP/com.google.android.voiceinteraction.GsaVoiceInteractionService"
        private const val ASSISTANT_ROLE = "android.app.role.ASSISTANT"
        private const val KEY_PREVIOUS = "previous_holder"
    }
}
