package me.ash.reader.ui.adaptive

/**
 * Coarse window-width buckets, aligned with the Material 3 window size classes.
 *
 * Only width is considered. Posture, orientation and fold state are deliberately ignored so that
 * the layout stays predictable when the user drags a freeform desktop window around or resizes a
 * split-screen pane.
 */
enum class AppSizeClass(val minWidthDp: Int) {
    /**
     * Phones in portrait, and narrow split-screen windows. Everything below this layer keeps its
     * upstream behaviour untouched.
     */
    Compact(0),

    /** Small tablets, phones in landscape, and wide split-screen windows. */
    Medium(600),

    /** Large tablets, unfolded foldables, and desktop windows. */
    Expanded(840);

    companion object {
        /**
         * Returns the bucket [widthDp] falls into.
         *
         * Never throws: [Compact] has a lower bound of 0, so it always matches and acts as the
         * floor. A width of 0 (container size not yet known) therefore resolves to [Compact],
         * which means "do not adapt" rather than "adapt to something arbitrary".
         */
        fun fromWidthDp(widthDp: Int): AppSizeClass = entries.last { widthDp >= it.minWidthDp }
    }
}
