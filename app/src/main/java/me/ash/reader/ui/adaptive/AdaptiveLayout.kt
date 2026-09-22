package me.ash.reader.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hard cap on how wide a single column of content may grow, in dp.
 *
 * Past this width the extra window space becomes symmetric gutter instead of being handed to the
 * content. This is what stops a 1280dp tablet from rendering 1200dp-wide feed rows with a feed
 * name pinned to the far left and its unread badge a hand-span away on the far right.
 */
val AdaptiveContentMaxWidth: Dp = 640.dp

/**
 * Upper bound for the final `fontScale`, so that boosting text on a tablet can never push the app
 * beyond the range Android itself tests (200%).
 */
const val MaxAdaptiveFontScale: Float = 2f

private const val MediumFontScaleMultiplier = 1.10f
private const val ExpandedFontScaleMultiplier = 1.20f

/**
 * Everything the layout layer exposes to screens.
 *
 * Screens read this through [LocalAdaptiveLayout] instead of hardcoding dp. Keeping every
 * size-related decision in one value object means adding a new dimension later is a change in this
 * file only, not a sweep across every component.
 */
data class AdaptiveLayoutSpec(
    val sizeClass: AppSizeClass,
    val fontScaleMultiplier: Float,
    val contentMaxWidth: Dp,
) {
    companion object {
        /**
         * Compact deliberately multiplies by exactly 1f. A phone must render identically to
         * upstream, otherwise this whole layer becomes a behavioural fork that upstream has a
         * reason to reject — and a reason to conflict with.
         */
        val Compact = AdaptiveLayoutSpec(AppSizeClass.Compact, 1f, AdaptiveContentMaxWidth)

        val Medium =
            AdaptiveLayoutSpec(AppSizeClass.Medium, MediumFontScaleMultiplier, AdaptiveContentMaxWidth)

        val Expanded =
            AdaptiveLayoutSpec(
                AppSizeClass.Expanded,
                ExpandedFontScaleMultiplier,
                AdaptiveContentMaxWidth,
            )

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
 * Exactly two things are provided:
 *
 *  - [LocalAdaptiveLayout], so screens can read sizes instead of hardcoding them.
 *  - `LocalDensity.fontScale`, scaled per size class. Only `sp` grows; dp paddings, icon sizes and
 *    row heights are untouched. That is deliberate: the reported problem is that text is too
 *    small, not that the layout is too tight, and scaling `density` instead would silently shrink
 *    the usable layout width.
 *
 * This composable is the single seam between the layout layer and the rest of the app. It is
 * installed once, high in the tree, so that no screen has to opt in individually.
 */
@Composable
fun ProvideAdaptiveLayout(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val widthDp = rememberWindowWidthDp()
    val spec = remember(widthDp) { AdaptiveLayoutSpec.of(AppSizeClass.fromWidthDp(widthDp)) }
    val scaledDensity =
        remember(density, spec.fontScaleMultiplier) {
            Density(
                density = density.density,
                fontScale =
                    (density.fontScale * spec.fontScaleMultiplier)
                        .coerceAtMost(MaxAdaptiveFontScale),
            )
        }

    CompositionLocalProvider(
        LocalAdaptiveLayout provides spec,
        LocalDensity provides scaledDensity,
    ) {
        content()
    }
}
