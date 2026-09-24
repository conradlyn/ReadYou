package me.ash.reader.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Scales one dimension for the current window size class.
 *
 * Material's defaults are tuned for a phone held in one hand: a 24dp icon inside a 48dp target, a
 * 64dp top app bar, a 20dp feed logo. Those are the right *physical* sizes on a 5-6" screen, and
 * they stay the same physical size on a tablet - which is exactly the problem. On an 8.8" tablet
 * the same icon occupies a visibly smaller fraction of the screen, so the whole interface reads as
 * undersized even though nothing is technically wrong.
 *
 * This is the one knob that fixes that. Apply it to icon glyphs and to the heights of bars that
 * hold them; do not apply it to padding, to text, or to anything the user can already size
 * themselves.
 *
 * ## What this deliberately does not scale
 *
 * - **Text.** The user has explicit, visible controls for that (`FeedsTextFontSizePreference` /
 *   `FlowTextFontSizePreference` / `ReadingTextFontSizePreference`). An implicit global multiplier
 *   on top of them would make the number in the settings page a lie - which is the mistake this
 *   layer already made once and reverted (see `AdaptiveLayout.kt`).
 * - **Touch targets.** `IconButton`'s 40dp container and the platform's 48dp minimum stay put. A
 *   bigger glyph inside the same target is a visual change; a bigger target is a hit-testing change,
 *   and that is a separate decision.
 * - **List row heights and paddings.** Those multiply with the font-size setting, so scaling them
 *   here would silently shrink how many rows fit on screen for a user who just wanted bigger type.
 *
 * ## Compact is the identity transform
 *
 * [AppSizeClass.Compact] carries a scale of exactly `1f`, and this function returns [base] itself
 * rather than computing `base * 1f`, so a phone measures and draws the same dp it did upstream.
 * That property is the whole reason this layer is acceptable as a fork: it cannot regress the
 * phone build, so there is nothing for a reviewer to weigh.
 */
@Composable
fun adaptiveSize(base: Dp): Dp = scaledDp(base, LocalAdaptiveLayout.current.scale)

/**
 * The arithmetic behind [adaptiveSize], split out so that the identity property above is pinned by
 * a unit test (`ui/adaptive/AdaptiveScaleTest.kt`) rather than only by this comment. The scale is
 * the one number the whole tablet adaptation rests on, and a plain JVM cannot compose a
 * `@Composable` to check it.
 *
 * Identity rather than `base * 1f`: same value either way, but skipping the multiply keeps the
 * intent obvious at the call site and avoids allocating a new Dp on every recomposition.
 */
fun scaledDp(base: Dp, scale: Float): Dp = if (scale == 1f) base else base * scale

/**
 * The raw multiplier behind [adaptiveSize].
 *
 * Exposed for the few places that need to scale a dimension it would be wrong to route through
 * [adaptiveSize] - for instance a size that is already derived from a user-controlled text size, or
 * an offset that has to stay proportional to a scaled height. Prefer [adaptiveSize] everywhere else;
 * a bare float is easy to apply to the wrong thing.
 */
@Composable
fun adaptiveScale(): Float = LocalAdaptiveLayout.current.scale
