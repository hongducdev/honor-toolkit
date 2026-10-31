package vn.hongducdev.honortoolkit.feature.circlesearch

import java.text.Normalizer
import java.util.Locale

/**
 * Wake phrase handling adapted from Trợ lý VXM's `Prefs.normalize` and `WakeFilter`. Vosk returns
 * lower-case text without punctuation, but users and other engines write "OK Google", so every
 * hypothesis is folded to a single canonical form before it is compared.
 *
 * Only "okay google" wakes the assistant, exactly like Trợ lý VXM. "hey google" stays in the
 * decoder grammar purely as a distractor: room noise that would otherwise be forced onto the wake
 * phrase is decoded as "hey google" instead and ignored.
 */
object WakePhrase {
    const val DISPLAY = "OK Google"

    /** Accepted canonical forms. "ok google" folds to "okay google" in [normalize]. */
    private val accepted = setOf("okay google")

    /**
     * Distractor vocabulary from VXM's `WakeFilter.grammar`. A two-word grammar makes the decoder
     * snap every gated sound onto a wake phrase; these extra words give it somewhere else to go.
     */
    private val distractors = listOf(
        "hey google",
        "okay",
        "hey",
        "oh",
        "yes",
        "no",
        "yeah",
        "hello",
        "alright",
        "sorry",
        "thank you",
        "one",
        "two",
        "three",
        "the",
        "and",
        "you",
        "what",
        "right",
        "well",
        "come on",
        "home",
        "cool",
        "baby",
        "money",
        "mama",
        "number",
        "boom",
        "[unk]",
    )

    val grammarJson: String =
        (listOf("okay google") + distractors).joinToString(separator = ",", prefix = "[", postfix = "]") {
            "\"$it\""
        }

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
