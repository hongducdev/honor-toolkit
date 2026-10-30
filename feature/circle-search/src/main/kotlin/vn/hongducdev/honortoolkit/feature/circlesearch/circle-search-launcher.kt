package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import org.lsposed.hiddenapibypass.HiddenApiBypass

sealed interface LaunchResult {
    data class Started(val detail: String) : LaunchResult
    data class Failed(val reason: String) : LaunchResult
}

// Circle to Search is opened as an assist session of the voice interaction service. Google's app
// only accepts the request for a known entry point and launcher tag, so both are passed explicitly.
// Gemini uses the same service with a session-style bundle instead of the screenshot entry point.
object CircleSearchLauncher {
    private const val VOICE_SERVICE = "voiceinteraction"
    private const val MANAGER_CLASS = "com.android.internal.app.IVoiceInteractionManagerService"
    private const val SHOW_SESSION = "showSessionFromSession"
    private const val ENTRY_POINT_KEY = "omni.entry_point"
    private const val ENTRY_POINT_HOME_GESTURE = 1
    private const val CIRCLE_FLAGS = 7
    private const val GEMINI_FLAGS = 3
    private const val LAUNCHER_TAG = "hyperOS_home"
    private const val INVOCATION_TYPE_KEY = "invocation_type"
    private const val INVOCATION_TYPE_POWER_LONG_PRESS = 7

    fun launch(): LaunchResult {
        val manager = runCatching { voiceManager() }
            .getOrElse { return LaunchResult.Failed("voiceinteraction: ${it.javaClass.simpleName}") }
            ?: return LaunchResult.Failed("voiceinteraction service not found")
        val managerClass = Class.forName(MANAGER_CLASS)
        val args = Bundle().apply {
            putLong("invocation_time_ms", SystemClock.elapsedRealtime())
            putInt(ENTRY_POINT_KEY, ENTRY_POINT_HOME_GESTURE)
        }
        return show(
            managerClass,
            manager,
            args,
            listOf(
                "flags=$CIRCLE_FLAGS tag=$LAUNCHER_TAG" to {
                    HiddenApiBypass.invoke(managerClass, manager, SHOW_SESSION, null, args, CIRCLE_FLAGS, LAUNCHER_TAG)
                },
                "flags=$CIRCLE_FLAGS" to {
                    HiddenApiBypass.invoke(managerClass, manager, SHOW_SESSION, null, args, CIRCLE_FLAGS)
                },
            ),
        )
    }

    fun launchGemini(): LaunchResult {
        val manager = runCatching { voiceManager() }
            .getOrElse { return LaunchResult.Failed("voiceinteraction: ${it.javaClass.simpleName}") }
            ?: return LaunchResult.Failed("voiceinteraction service not found")
        val managerClass = Class.forName(MANAGER_CLASS)
        val args = Bundle().apply {
            putLong("invocation_time_ms", SystemClock.elapsedRealtime())
            putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_POWER_LONG_PRESS)
            putString("invocation_source", "POWER_LONG_PRESS")
            putBoolean("request_assist_structure", true)
            putBoolean("request_assist_screenshot", true)
        }
        return show(
            managerClass,
            manager,
            args,
            listOf(
                "invocation_type=$INVOCATION_TYPE_POWER_LONG_PRESS flags=$GEMINI_FLAGS tag=$LAUNCHER_TAG" to {
                    HiddenApiBypass.invoke(managerClass, manager, SHOW_SESSION, null, args, GEMINI_FLAGS, LAUNCHER_TAG)
                },
                "invocation_type=$INVOCATION_TYPE_POWER_LONG_PRESS flags=$GEMINI_FLAGS" to {
                    HiddenApiBypass.invoke(managerClass, manager, SHOW_SESSION, null, args, GEMINI_FLAGS)
                },
                "invocation_type=$INVOCATION_TYPE_POWER_LONG_PRESS flags=$CIRCLE_FLAGS tag=$LAUNCHER_TAG" to {
                    HiddenApiBypass.invoke(managerClass, manager, SHOW_SESSION, null, args, CIRCLE_FLAGS, LAUNCHER_TAG)
                },
            ),
        )
    }

    private fun show(managerClass: Class<*>, manager: Any, args: Bundle, attempts: List<Pair<String, () -> Any?>>): LaunchResult {
        var lastError = "rejected by system"
        for ((detail, attempt) in attempts) {
            runCatching(attempt)
                .onSuccess { if (it == true) return LaunchResult.Started(detail) else lastError = "session refused" }
                .onFailure { lastError = it.javaClass.simpleName }
        }
        return LaunchResult.Failed(lastError)
    }

    /** Package of the assistant sessions are routed to, so a refusal says which app refused it. */
    fun activeAssistant(context: Context): String {
        val managerClass = runCatching { Class.forName(MANAGER_CLASS) }.getOrNull()
        val manager = runCatching { voiceManager() }.getOrNull()
        if (managerClass != null && manager != null) {
            runCatching {
                val component = HiddenApiBypass.invoke(managerClass, manager, "getActiveServiceComponentName") as? ComponentName
                if (component != null) return component.packageName
            }
        }
        val raw = Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        val component = raw?.let { ComponentName.unflattenFromString(it) }
        return component?.packageName ?: context.getString(R.string.cs_assistant_unknown)
    }

    private fun voiceManager(): Any? {
        HiddenApiBypass.addHiddenApiExemptions("")
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, VOICE_SERVICE) as IBinder? ?: return null
        return Class.forName("$MANAGER_CLASS\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
    }
}
