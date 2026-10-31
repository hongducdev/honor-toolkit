package vn.hongducdev.honortoolkit.feature.tune

/** Sections of the tuning page. Order here is the order on screen. */
internal enum class TuneGroup { Performance, Notifications, ChinaApps, Network }

/** Every control the page exposes. Ids are stable so a failed write can be matched to its row. */
enum class TuneId {
    RefreshRate,
    AnimationScale,
    TouchBoost,
    GentleNotifications,
    NotificationCooldown,
    HeadsUp,
    BannerContent,
    LockScreenDetail,
    JobBatching,
    HiBoard,
    HonorSearch,
    MagazineDownload,
    YoyoNavWake,
    MarketAutoUpdate,
    AdPersonalization,
    CaptivePortal,
    CallerIdMatch,
    Volte5g,
    SuplGps,
    PrivateDnsAdBlock,
}

internal enum class TuneNamespace(val cli: String) { System("system"), Secure("secure"), Global("global") }

internal data class SettingKey(val namespace: TuneNamespace, val key: String) {
    /** `secure/hw_screen_freq`, used as the marker in a batch read. */
    val path: String get() = "${namespace.cli}/$key"

    /** `secure hw_screen_freq`, the argument pair `settings` expects. */
    val command: String get() = "${namespace.cli} $key"
}

/** One setting and the value it takes when the row is on and when it is off. */
internal data class TuneWrite(val key: SettingKey, val onValue: String, val offValue: String)

/**
 * How to recognise one state of a toggle. [negate] means "anything except [value] counts",
 * which is how a deleted or ROM-specific setting is treated as off without guessing its default.
 */
internal data class ReadRule(val key: SettingKey, val value: String, val negate: Boolean = false) {
    fun matches(actual: String?): Boolean = if (negate) actual != value else actual == value
}

internal data class ToggleSpec(
    val id: TuneId,
    val group: TuneGroup,
    val writes: List<TuneWrite> = emptyList(),
    val onRules: List<ReadRule> = writes.map { ReadRule(it.key, it.onValue) },
    val offRules: List<ReadRule> = writes.map { ReadRule(it.key, it.offValue) },
    val extraOn: List<String> = emptyList(),
    val extraOff: List<String> = emptyList(),
) {
    val readKeys: List<SettingKey> get() = (onRules + offRules).map { it.key }.distinct()

    val vendorKeys: List<SettingKey> get() = writes.map { it.key }.distinct()

    /**
     * Keeps only the vendor keys this ROM really has, so a write never creates a key nothing reads.
     * Null when the row writes vendor keys and the ROM has none of them. Rows without [writes]
     * use standard AOSP keys that are valid to create, so they pass through unchanged.
     */
    fun supportedBy(present: Set<SettingKey>): ToggleSpec? {
        if (writes.isEmpty()) return this
        if (vendorKeys.none { it in present }) return null
        fun keep(rule: ReadRule) = rule.key !in vendorKeys || rule.key in present
        return copy(
            writes = writes.filter { it.key in present },
            onRules = onRules.filter(::keep),
            offRules = offRules.filter(::keep),
        )
    }

    fun commands(enabled: Boolean): List<String> =
        writes.map { put(it.key, if (enabled) it.onValue else it.offValue) } + if (enabled) extraOn else extraOff

    /** False when the spec has no rule for that state, so the caller can report "unknown" instead of guessing. */
    fun matches(enabled: Boolean, readings: Map<SettingKey, String?>): Boolean {
        val rules = if (enabled) onRules else offRules
        return rules.isNotEmpty() && rules.all { it.matches(readings[it.key]) }
    }
}

internal data class ChoiceOption(val value: String, val label: String, val commands: List<String>)

internal data class ChoiceSpec(
    val id: TuneId,
    val group: TuneGroup,
    val readKey: SettingKey,
    val options: List<ChoiceOption>,
) {
    fun option(value: String): ChoiceOption? = options.firstOrNull { it.value == value }

    fun matches(value: String, actual: String?): Boolean = actual == value

    /** The option value that corresponds to the stored reading, used to preselect a chip. */
    fun selectionOf(actual: String?): String? = options.firstOrNull { matches(it.value, actual) }?.value
}

private fun put(key: SettingKey, value: String): String = "settings put ${key.command} $value"
private fun delete(key: SettingKey): String = "settings delete ${key.command}"
private fun write(namespace: TuneNamespace, key: String, on: String, off: String): TuneWrite =
    TuneWrite(SettingKey(namespace, key), on, off)

/**
 * The MagicOS settings that China-ROM users actually need. The values come from the wiring
 * used by MagicTune 2.6 on the same firmware family; every write is read back, so a key this
 * ROM ignores shows up as refused instead of as a success.
 */
internal object TuneCatalog {
    private const val CAPTIVE_HTTPS = "https://connectivitycheck.gstatic.com/generate_204"
    private const val CAPTIVE_HTTP = "http://connectivitycheck.gstatic.com/generate_204"
    private const val JOB_CONSTANTS = "min_ready_non_active_jobs_count=1,max_non_active_job_batch_delay_ms=0"
    private const val ADBLOCK_DNS = "dns.adguard-dns.com"
    private const val SUPL_HOST = "supl.google.com"
    private const val SUPL_PORT = "7275"

    /** MagicOS "Tốc độ làm mới màn hình": 0 Chuẩn (60 Hz), 1 Cao (120 Hz), 2 Động. Confirmed on PTP-AN10 / MagicOS 11. */
    private val refreshMode = SettingKey(TuneNamespace.Secure, "hw_screen_freq")
    private val globalMinMatch = SettingKey(TuneNamespace.Global, "min_match")
    private val systemMinMatch = SettingKey(TuneNamespace.System, "min_match")
    private val jobConstants = SettingKey(TuneNamespace.Global, "job_scheduler_constants")
    private val privateDnsMode = SettingKey(TuneNamespace.Global, "private_dns_mode")
    private val privateDnsSpecifier = SettingKey(TuneNamespace.Global, "private_dns_specifier")
    private val captivMode = SettingKey(TuneNamespace.Global, "captive_portal_mode")
    private val captivHttps =SettingKey(TuneNamespace.Global, "captive_portal_https_url")
    private val captivHttp = SettingKey(TuneNamespace.Global, "captive_portal_http_url")
    private val suplHost = SettingKey(TuneNamespace.Global, "supl_host")
    private val suplPort = SettingKey(TuneNamespace.Global, "supl_port")

    val toggles: List<ToggleSpec> = listOf(
        ToggleSpec(
            id = TuneId.TouchBoost,
            group = TuneGroup.Performance,
            writes = listOf(
                write(TuneNamespace.Secure, "touch_response_time", "0", "1"),
                write(TuneNamespace.Secure, "touch_responsiveness_level", "1", "0"),
            ),
        ),
        ToggleSpec(
            id = TuneId.GentleNotifications,
            group = TuneGroup.Notifications,
            writes = listOf(
                write(TuneNamespace.Secure, "hnnotification_classification_state", "0", "1"),
            ),
        ),
        ToggleSpec(
            id = TuneId.NotificationCooldown,
            group = TuneGroup.Notifications,
            writes = listOf(write(TuneNamespace.System, "notification_cooldown_enabled", "0", "1")),
        ),
        ToggleSpec(
            id = TuneId.HeadsUp,
            group = TuneGroup.Notifications,
            writes = listOf(
                write(TuneNamespace.Global, "heads_up_notifications_enabled", "1", "0"),
                write(TuneNamespace.System, "notification_banner_enabled", "1", "0"),
            ),
        ),
        ToggleSpec(
            id = TuneId.BannerContent,
            group = TuneGroup.Notifications,
            writes = listOf(write(TuneNamespace.Secure, "banner_allow_private_notifications", "0", "1")),
        ),
        ToggleSpec(
            id = TuneId.LockScreenDetail,
            group = TuneGroup.Notifications,
            writes = listOf(
                write(TuneNamespace.Secure, "lock_screen_allow_private_notifications", "1", "0"),
                write(TuneNamespace.System, "lock_screen_details_notification", "1", "0"),
            ),
        ),
        ToggleSpec(
            id = TuneId.JobBatching,
            group = TuneGroup.Notifications,
            onRules = listOf(ReadRule(jobConstants, JOB_CONSTANTS)),
            offRules = listOf(ReadRule(jobConstants, JOB_CONSTANTS, negate = true)),
            extraOn = listOf(put(jobConstants, JOB_CONSTANTS)),
            extraOff = listOf(delete(jobConstants)),
        ),
        ToggleSpec(
            id = TuneId.HiBoard,
            group = TuneGroup.ChinaApps,
            writes = listOf(
                write(TuneNamespace.Secure, "hiboard_switch", "0", "1"),
                write(TuneNamespace.System, "hiboard_switch", "0", "1"),
            ),
        ),
        ToggleSpec(
            id = TuneId.HonorSearch,
            group = TuneGroup.ChinaApps,
            writes = listOf(
                write(TuneNamespace.Secure, "honor_search_disabled", "1", "0"),
                write(TuneNamespace.Secure, "hiboard_search_switch", "0", "1"),
                write(TuneNamespace.System, "hiboard_search_switch", "0", "1"),
            ),
        ),
        ToggleSpec(
            id = TuneId.MagazineDownload,
            group = TuneGroup.ChinaApps,
            writes = listOf(write(TuneNamespace.System, "auto_download_magazine_wifi", "0", "1")),
        ),
        ToggleSpec(
            id = TuneId.YoyoNavWake,
            group = TuneGroup.ChinaApps,
            writes = listOf(
                write(TuneNamespace.Secure, "navigation_bar_wakeup_yoyo", "0", "1"),
                write(TuneNamespace.Secure, "yoyo_navigation_bar_wakeup", "0", "1"),
                write(TuneNamespace.System, "navigation_bar_wakeup_yoyo", "0", "1"),
            ),
        ),
        ToggleSpec(
            id = TuneId.MarketAutoUpdate,
            group = TuneGroup.ChinaApps,
            writes = listOf(
                write(TuneNamespace.Global, "auto_update_app", "0", "1"),
                write(TuneNamespace.Secure, "auto_update_app", "0", "1"),
            ),
        ),
        ToggleSpec(
            id = TuneId.AdPersonalization,
            group = TuneGroup.ChinaApps,
            writes = listOf(
                write(TuneNamespace.Secure, "advertising_id_opt_out", "1", "0"),
                write(TuneNamespace.Global, "personalized_ad_enabled", "0", "1"),
                write(TuneNamespace.Secure, "limit_ad_tracking", "1", "0"),
            ),
        ),
        ToggleSpec(
            id = TuneId.CaptivePortal,
            group = TuneGroup.Network,
            onRules = listOf(
                ReadRule(captivMode, "1"),
                ReadRule(captivHttps, CAPTIVE_HTTPS),
            ),
            offRules = listOf(ReadRule(captivHttps, CAPTIVE_HTTPS, negate = true)),
            extraOn = listOf(put(captivMode, "1"), put(captivHttps, CAPTIVE_HTTPS), put(captivHttp, CAPTIVE_HTTP)),
            extraOff = listOf(delete(captivMode), delete(captivHttps), delete(captivHttp)),
        ),
        ToggleSpec(
            id = TuneId.CallerIdMatch,
            group = TuneGroup.Network,
            onRules = listOf(ReadRule(systemMinMatch, "7"), ReadRule(globalMinMatch, "7")),
            offRules = listOf(ReadRule(systemMinMatch, "7", negate = true), ReadRule(globalMinMatch, "7", negate = true)),
            extraOn = listOf(put(systemMinMatch, "7"), put(globalMinMatch, "7")),
            extraOff = listOf(delete(systemMinMatch), delete(globalMinMatch)),
        ),
        ToggleSpec(
            id = TuneId.Volte5g,
            group = TuneGroup.Network,
            writes = listOf(
                write(TuneNamespace.System, "hw_volte_user_switch", "1", "0"),
                write(TuneNamespace.Global, "fiveg_user_switch", "1", "0"),
            ),
        ),
        ToggleSpec(
            id = TuneId.SuplGps,
            group = TuneGroup.Network,
            onRules = listOf(ReadRule(suplHost, SUPL_HOST), ReadRule(suplPort, SUPL_PORT)),
            offRules = listOf(ReadRule(suplHost, SUPL_HOST, negate = true)),
            extraOn = listOf(
                put(suplHost, SUPL_HOST),
                put(suplPort, SUPL_PORT),
                put(SettingKey(TuneNamespace.Global, "assisted_gps_enabled"), "1"),
            ),
            extraOff = listOf(delete(suplHost), delete(suplPort)),
        ),
        ToggleSpec(
            id = TuneId.PrivateDnsAdBlock,
            group = TuneGroup.Network,
            onRules = listOf(
                ReadRule(privateDnsMode, "hostname"),
                ReadRule(privateDnsSpecifier, ADBLOCK_DNS),
            ),
            offRules = listOf(ReadRule(privateDnsMode, "hostname", negate = true)),
            extraOn = listOf(
                put(privateDnsMode, "hostname"),
                put(privateDnsSpecifier, ADBLOCK_DNS),
            ),
            extraOff = listOf(
                put(privateDnsMode, "opportunistic"),
                delete(privateDnsSpecifier),
            ),
        ),
    )

    val choices: List<ChoiceSpec> = listOf(
        ChoiceSpec(
            id = TuneId.RefreshRate,
            group = TuneGroup.Performance,
            readKey = refreshMode,
            options = listOf(
                "0" to "Chuẩn 60 Hz",
                "1" to "Cao 120 Hz",
                "2" to "Động",
            ).map { (value, label) ->
                ChoiceOption(value = value, label = label, commands = listOf(put(refreshMode, value)))
            },
        ),
        ChoiceSpec(
            id = TuneId.AnimationScale,
            group = TuneGroup.Performance,
            readKey = SettingKey(TuneNamespace.Global, "window_animation_scale"),
            options = listOf("0.5" to "0,5×", "1.0" to "1,0×").map { (value, label) ->
                ChoiceOption(
                    value = value,
                    label = label,
                    commands = listOf(
                        put(SettingKey(TuneNamespace.Global, "window_animation_scale"), value),
                        put(SettingKey(TuneNamespace.Global, "transition_animation_scale"), value),
                        put(SettingKey(TuneNamespace.Global, "animator_duration_scale"), value),
                    ),
                )
            },
        ),
    )

    /** Toggles and choices the "recommended for China ROM" button applies, in order. */
    val recommended: List<TuneId> = listOf(
        TuneId.TouchBoost,
        TuneId.AnimationScale,
        TuneId.GentleNotifications,
        TuneId.NotificationCooldown,
        TuneId.HeadsUp,
        TuneId.BannerContent,
        TuneId.LockScreenDetail,
        TuneId.JobBatching,
        TuneId.HiBoard,
        TuneId.HonorSearch,
        TuneId.MagazineDownload,
        TuneId.YoyoNavWake,
        TuneId.MarketAutoUpdate,
        TuneId.AdPersonalization,
        TuneId.Volte5g,
    )

    val recommendedChoices: Map<TuneId, String> = mapOf(TuneId.AnimationScale to "0.5")

    val allReadKeys: List<SettingKey> =
        (toggles.flatMap { it.readKeys } + choices.map { it.readKey }).distinct()

    /**
     * Keys the user commonly sets themselves (their own Private DNS, ad-tracking choice). They are
     * left alone by "restore defaults"; turning the matching row off is the only way to touch them.
     */
    private val userOwnedKeys: Set<SettingKey> = setOf(
        privateDnsMode,
        privateDnsSpecifier,
        SettingKey(TuneNamespace.Secure, "limit_ad_tracking"),
        SettingKey(TuneNamespace.Secure, "advertising_id_opt_out"),
    )

    /**
     * Keys "restore defaults" removes. Deleting lets the ROM fall back to its own default instead
     * of this page guessing a stock value. The pre-install value is not recorded, so anything the
     * user may have set deliberately is in [userOwnedKeys] and excluded.
     */
    val restoreKeys: List<SettingKey> = (
        allReadKeys + listOf(
            SettingKey(TuneNamespace.Global, "transition_animation_scale"),
            SettingKey(TuneNamespace.Global, "animator_duration_scale"),
        )
        ).distinct().filterNot { it in userOwnedKeys }

    val gpsResetCommands: List<String> = listOf(
        "cmd location send-extra-command gps delete_aiding_data",
        "cmd location send-extra-command gps force_xtra_injection",
        "cmd location send-extra-command gps force_time_injection",
    )

    /** Delete every setting this page can write so the ROM uses its compiled defaults again. */
    val restoreCommands: List<String> = restoreKeys.map { delete(it) }

    fun toggle(id: TuneId): ToggleSpec = toggles.first { it.id == id }

    fun choice(id: TuneId): ChoiceSpec = choices.first { it.id == id }
}
