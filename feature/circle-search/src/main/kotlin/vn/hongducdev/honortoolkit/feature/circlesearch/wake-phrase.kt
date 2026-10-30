package vn.hongducdev.honortoolkit.feature.circlesearch

import java.text.Normalizer
import java.util.Locale

/**
 * Wake phrase handling adapted from Trợ lý VXM's `Prefs.normalize` and `WakeFilter`. Vosk returns
 * lower-case text without punctuation, but users and other engines write "OK Google", so every
 * hypothesis is folded to a single canonical form before it is compared.
 *
 * The grammar is deliberately tiny: a two-word vocabulary keeps the offline decoder fast enough
 * for a phone and stops it from decoding arbitrary speech while the service runs all day.
 */
object WakePhrase {
    const val DISPLAY = "OK Google"

    /** Accepted canonical forms. "ok google" folds to "okay google" in [normalize]. */
    private val accepted = setOf("okay google", "hey google")

    val grammarJson: String = """["okay google","hey google","[unk]"]"""

    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val folded = raw.lowercase(Locale.ROOT)
            .replace('\u0111', 'd')
            .replace(Regex("\\bok\\b"), "okay")
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun matches(raw: String?): Boolean = normalize(raw) in accepted
}
