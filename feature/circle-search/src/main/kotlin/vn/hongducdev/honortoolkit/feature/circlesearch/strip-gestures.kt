package vn.hongducdev.honortoolkit.feature.circlesearch

/** Gesture recognised on the nav-bar strip. Swipes are deliberately not handled: the strip sits on the system gesture handle, so consuming them would break back/home gestures. */
enum class StripGesture(val key: String, val labelRes: Int) {
    Tap("tap", R.string.gesture_tap),
    Double("double", R.string.gesture_double),
    Hold("hold", R.string.gesture_hold),
}

/** What a strip gesture runs. Keys are persisted in [TriggerConfig]. */
enum class StripAction(val key: String, val labelRes: Int) {
    None("none", R.string.action_none),
    Circle("circle", R.string.action_circle),
    Gemini("gemini", R.string.action_gemini),
    Assistant("assistant", R.string.action_assistant);

    companion object {
        fun fromKey(key: String?): StripAction = entries.firstOrNull { it.key == key } ?: None

        /** Same defaults as the reference app: tap does nothing, double tap opens Gemini, hold opens Circle to Search. */
        fun defaultFor(gesture: StripGesture): StripAction = when (gesture) {
            StripGesture.Tap -> None
            StripGesture.Double -> Gemini
            StripGesture.Hold -> Circle
        }
    }
}
