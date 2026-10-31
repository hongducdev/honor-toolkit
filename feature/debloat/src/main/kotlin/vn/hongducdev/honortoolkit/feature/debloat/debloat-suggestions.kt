package vn.hongducdev.honortoolkit.feature.debloat

/** How confident the advice is, not how safe the package is on any given ROM. */
enum class SuggestionLevel { Safe, Usually }

data class PackageSuggestion(val level: SuggestionLevel, val reason: Int)

/**
 * Advisory list carried over from the package set MagicTune 2.6 groups as "nên tắt" on a China-ROM
 * Honor phone. Nothing here is verified on this device: the badge is a hint, the guard and the
 * journal still decide what can be disabled and how it is restored.
 */
internal object DebloatSuggestions {
    fun of(packageName: String): PackageSuggestion? = entries[packageName]

    val entries: Map<String, PackageSuggestion> = mapOf(
        "com.hihonor.video" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_video),
        "com.hihonor.gamecenter" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_gamecenter),
        "com.hihonor.search" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_search),
        "com.hihonor.browser" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_browser),
        "com.hihonor.magazinesettings" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_magazine),
        "com.hihonor.music" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_music),
        "com.hihonor.reader" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_reader),
        "com.hihonor.mall" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_mall),
        "com.hihonor.live" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_live),
        "com.hihonor.hiboard" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_hiboard),
        "com.huawei.powergenie" to PackageSuggestion(SuggestionLevel.Safe, R.string.debloat_tip_powergenie),
        "com.hihonor.powergenius" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_powergenius),
        "com.hihonor.iaware" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_iaware),
        "com.hihonor.appmarket" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_appmarket),
        "com.hihonor.assistant" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_assistant),
        "com.hihonor.wallet" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_wallet),
        "com.hihonor.health" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_health),
        "com.hihonor.cloud" to PackageSuggestion(SuggestionLevel.Usually, R.string.debloat_tip_cloud),
    )
}
