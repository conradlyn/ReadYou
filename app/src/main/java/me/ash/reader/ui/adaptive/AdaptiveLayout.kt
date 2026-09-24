package me.ash.reader.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hard cap on how wide a single column of content may grow, in dp.
 *
 * Past this width the extra window space becomes symmetric gutter instead of being handed to the
 * content. This is what stops a 1280dp tablet from rendering 1200dp-wide feed rows with a feed
 * name pinned to the far left and its unread badge a hand-span away on the far right.
 *
 * Deliberately not one of the reading-page widths. Upstream has `MediumContentWidth` (600.dp) and
 * `ExpandedContentWidth` (768.dp) in `ui/component/reader/Styles.kt`, but those exist to bound a
 * *single column of prose*, where line length is the binding constraint. A list row carries a
 * title, a description and a trailing badge, so it tolerates - and reads better with - a slightly
 * wider column than a paragraph does. 640dp sits between the two upstream values, which keeps the
 * change of column width when navigating between the feeds page and the flow page small enough
 * that it does not read as a layout bug.
 */
val AdaptiveContentMaxWidth: Dp = 640.dp

/**
 * How much icons and control heights grow on a tablet, per size class.
 *
 * Material's sizes are tuned for a phone, and dp is an absolute unit - a 24dp icon is the same
 * physical size on an 8.8" tablet as on a 6" phone, so on the tablet it reads as undersized. These
 * multipliers restore the *relative* proportion instead of leaving the user with a phone interface
 * stretched over a larger screen.
 *
 * Deliberately mild. This is not a zoom: the icons grow by 15-25%, not by the 40-60% that a naive
 * "scale with the diagonal" calculation would suggest. Past roughly 1.3 the glyphs start to crowd
 * the fixed 40dp `IconButton` container they sit in, and the bar heights have to grow with them to
 * keep the result from looking cramped.
 *
 * [AppSizeClass.Medium] matters more than it looks: an 8.8" tablet held in portrait is about 800dp
 * wide, which is Medium, not Expanded. Skipping Medium would mean the adaptation disappears in the
 * orientation this app is most often read in.
 */
const val AdaptiveScaleCompact = 1f
const val AdaptiveScaleMedium = 1.15f
const val AdaptiveScaleExpanded = 1.25f

/**
 * Everything the layout layer exposes to screens.
 *
 * Screens read this through [LocalAdaptiveLayout] instead of hardcoding dp. Keeping every
 * size-related decision in one value object means adding a new dimension later is a change in this
 * file only, not a sweep across every component.
 */
data class AdaptiveLayoutSpec(
    val sizeClass: AppSizeClass,
    val contentMaxWidth: Dp,
    val scale: Float,
) {
    companion object {
        /**
         * A phone must render identically to upstream, otherwise this whole layer becomes a
         * behavioural fork that upstream has a reason to reject — and a reason to conflict with.
         * [Compact] therefore carries no adaptation at all: the content gutter resolves to exactly
         * zero (see `rememberAdaptiveContentGutter`), the scale is exactly 1 (see `adaptiveSize`)
         * and no other dimension is touched.
         */
        val Compact =
            AdaptiveLayoutSpec(AppSizeClass.Compact, AdaptiveContentMaxWidth, AdaptiveScaleCompact)

        val Medium =
            AdaptiveLayoutSpec(AppSizeClass.Medium, AdaptiveContentMaxWidth, AdaptiveScaleMedium)

        val Expanded =
            AdaptiveLayoutSpec(AppSizeClass.Expanded, AdaptiveContentMaxWidth, AdaptiveScaleExpanded)

        fun of(sizeClass: AppSizeClass): AdaptiveLayoutSpec =
            when (sizeClass) {
                AppSizeClass.Compact -> Compact
                AppSizeClass.Medium -> Medium
                AppSizeClass.Expanded -> Expanded
            }
    }
}

/**
 * `static` on purpose. The value only changes when the window crosses a size-class breakpoint,
 * which is rare, and such a change is meant to invalidate the whole subtree anyway — so paying for
 * fine-grained read tracking would buy nothing.
 */
val LocalAdaptiveLayout = staticCompositionLocalOf { AdaptiveLayoutSpec.Compact }

/**
 * Reads the current window width and installs the matching [AdaptiveLayoutSpec].
 *
 * Exactly one thing is provided: [LocalAdaptiveLayout], so screens can read sizes instead of
 * hardcoding them. This composable is the single seam between the layout layer and the rest of the
 * app. It is installed once, high in the tree (see `ui/theme/Theme.kt`), so that no screen has to
 * opt in individually.
 *
 * ## Why this does not scale `LocalDensity.fontScale`
 *
 * It used to, per size class, on the theory that "text is too small on a tablet". It was removed
 * for two reasons, either of which alone would be enough:
 *
 *  1. It multiplied with the explicit list font size rather than replacing it. The lists render at
 *     `sizeSp/16` of their stock style (`ui/component/ListFonts.kt`), and that result is still an
 *     `sp` value, so it picks up `fontScale` again at draw time. A user who picked 32sp saw 38.4sp
 *     on an Expanded window while the settings page said 32sp - and the effective size jumped as
 *     the window crossed a breakpoint.
 *  2. `Density(density, fontScale)` returns a plain `DensityImpl`, but the platform installs a
 *     `DensityWithConverter` when it needs to apply Android 14+ non-linear font scaling on top of
 *     `fontScale`. Overriding `LocalDensity` dropped that converter, so "Compact is an identity
 *     transform" became a claim about the OS version rather than about this code.
 *
 * Text size is the user's to set instead; the feeds and flow pages expose an explicit control
 * (`FeedsTextFontSizePreference` / `FlowTextFontSizePreference`).
 *
 * ## Window width vs. available width
 *
 * The width read here is the *window* width, which is the right input for a size class: a size
 * class is a property of the window, and this composable sits above the navigation host. The
 * content gutter is computed further down instead, from the width that actually reaches a page -
 * see `rememberAdaptiveContentGutter`.
 */
@Composable
fun ProvideAdaptiveLayout(content: @Composable () -> Unit) {
    val widthDp = rememberWindowWidthDp()
    val spec = remember(widthDp) { AdaptiveLayoutSpec.of(AppSizeClass.fromWidthDp(widthDp)) }

    CompositionLocalProvider(LocalAdaptiveLayout provides spec) { content() }
}
