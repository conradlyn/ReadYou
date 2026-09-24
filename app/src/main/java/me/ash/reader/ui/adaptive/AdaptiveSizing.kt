package me.ash.reader.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 *   layer already made once and reverted (see `AdaptiveLayout.kt`). The interface's *own* text is a
 *   separate and equally explicit control: `UiTextScalePreference` via `ProvideUiTextScale`.
 *
 * ## What this used to leave alone, and no longer does
 *
 * Both of the following were skipped in the first pass and picked up in the second, at the user's
 * request. They are spelled out because "this layer does not scale X" is the kind of claim that gets
 * believed later:
 *
 * - **Touch targets.** `IconButton` reserves 48dp through `minimumInteractiveComponentSize()`, and
 *   the container is grown *on top of* that reservation, never in place of it. See
 *   [adaptiveIconButtonContainer] for why the phone path passes no size at all.
 * - **List row heights and paddings.** Those multiply with the font-size setting, so scaling them
 *   does change how many rows fit on screen. That is the intended trade now, but it is also why they
 *   are scaled at the call sites (`GroupItem` / `FeedItem` / `ArticleItem`) rather than here: the
 *   amounts differ per row, and the icon inset in `ArticleItem` has to move together with the icon.
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

/**
 * Grows an `IconButton`'s container on tablet widths, and leaves the modifier untouched on a phone.
 *
 * ## Why "untouched on a phone" is the point, not a micro-optimisation
 *
 * `IconButton` builds its box as `modifier.minimumInteractiveComponentSize().size(40.dp)`, and
 * Material's own KDoc for `minimumInteractiveComponentSize` warns that *"for this modifier to take
 * effect, it must come before any size modifiers on the element that might limit its constraints"*.
 * A `Modifier.size` handed in here lands **before** it, so on a phone it would cap the 48dp that
 * `IconButton` reserves: the touch target would silently drop from 48dp to whatever we passed. That
 * is a regression dressed up as an improvement, so on a phone nothing is passed at all.
 *
 * (Verified against the material3 1.4.0 sources rather than assumed: `SmallIconButtonTokens`
 * `ContainerHeight` is 40dp and `IconButtonDefaults.smallContainerSize()` resolves to the same 40dp,
 * while `InteractiveComponentSize` reserves 48dp. The chain order above is `IconButton.kt` as
 * shipped.)
 *
 * ## Why the 48dp floor
 *
 * Once we do grow it, the floor is what keeps the reservation intact: the stock 40dp scaled by the
 * `Medium` factor of 1.15 is only 46dp, which would still be a shrink. So the container is
 * `max(adaptiveSize(40.dp), 48.dp)` - 48dp on `Medium`, 50dp on `Expanded`.
 */
@Composable
fun Modifier.adaptiveIconButtonContainer(): Modifier {
    val scale = adaptiveScale()
    if (scale == 1f) return this
    // 40dp is `SmallIconButtonTokens.ContainerHeight`, the stock container.
    return this.size(maxOf(adaptiveSize(40.dp), 48.dp))
}
