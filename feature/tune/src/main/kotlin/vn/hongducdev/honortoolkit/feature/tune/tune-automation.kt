package vn.hongducdev.honortoolkit.feature.tune

/**
 * The subset of the tune catalogue that automation can switch. Only toggles made of plain
 * `settings put` writes are exposed: a toggle that also needs `settings delete` or a shell-only
 * step stays on the tune page, because the automation action writes settings directly and has no
 * shell to fall back on. [titleRes] is one of this module's strings.
 */
data class TuneAutomationWrite(
    val scope: String,
    val key: String,
    val onValue: String,
    val offValue: String,
)

data class TuneAutomationFeature(
    val id: String,
    val group: String,
    val titleRes: Int,
    val writes: List<TuneAutomationWrite>,
)

/** Bridge used by `:feature:automation` for its "Toolkit feature" action. */
object TuneAutomation {
    fun features(): List<TuneAutomationFeature> = TuneCatalog.toggles
        .filter { it.writes.isNotEmpty() }
        .map { spec ->
            TuneAutomationFeature(
                id = spec.id.name,
                group = spec.group.name,
                titleRes = toggleTitle(spec.id),
                writes = spec.writes.map {
                    TuneAutomationWrite(
                        scope = it.key.namespace.cli,
                        key = it.key.key,
                        onValue = it.onValue,
                        offValue = it.offValue,
                    )
                },
            )
        }

    fun feature(id: String): TuneAutomationFeature? = features().firstOrNull { it.id == id }

    /**
     * Every key the tune page can write; automation treats these as its safe allowlist. Plain-write
     * toggles carry their values; the rest (choices and toggles with extra commands) contribute only
     * their key, because the automation action writes one value, not a command sequence.
     */
    fun writableKeys(): List<TuneAutomationWrite> = (
        features().flatMap { it.writes } +
            (TuneCatalog.toggles.flatMap { it.readKeys } + TuneCatalog.choices.map { it.readKey })
                .map { TuneAutomationWrite(it.namespace.cli, it.key, "", "") }
        ).distinctBy { it.scope to it.key }
}

/** Same labels as the tune page, kept here so automation does not depend on a Compose file. */
internal fun toggleTitle(id: TuneId): Int = when (id) {
    TuneId.TouchBoost -> R.string.tune_touch_boost
    TuneId.GentleNotifications -> R.string.tune_gentle
    TuneId.NotificationCooldown -> R.string.tune_cooldown
    TuneId.HeadsUp -> R.string.tune_heads_up
    TuneId.BannerContent -> R.string.tune_banner_content
    TuneId.LockScreenDetail -> R.string.tune_lock_screen
    TuneId.JobBatching -> R.string.tune_batching
    TuneId.HiBoard -> R.string.tune_hiboard
    TuneId.HonorSearch -> R.string.tune_honor_search
    TuneId.MagazineDownload -> R.string.tune_magazine
    TuneId.YoyoNavWake -> R.string.tune_yoyo
    TuneId.MarketAutoUpdate -> R.string.tune_market
    TuneId.AdPersonalization -> R.string.tune_ads
    TuneId.CaptivePortal -> R.string.tune_captive
    TuneId.CallerIdMatch -> R.string.tune_caller
    TuneId.Volte5g -> R.string.tune_volte
    TuneId.SuplGps -> R.string.tune_supl
    TuneId.PrivateDnsAdBlock -> R.string.tune_dns
    else -> R.string.tune_title
}
