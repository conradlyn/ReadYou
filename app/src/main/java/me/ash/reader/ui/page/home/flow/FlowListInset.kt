package me.ash.reader.ui.page.home.flow

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.adaptive.adaptiveScale
import me.ash.reader.ui.adaptive.scaledDp

/**
 * The horizontal padding an article row applies twice: once to inset its card from the edge of the
 * list, and again to inset the text from the edge of that card.
 *
 * The row's *vertical* padding happens to be the same 12dp, but it is a different concern and is
 * deliberately left as its own literal at the call site. Routing it through this constant would mean
 * that a future change to the vertical rhythm silently moved the left edge of every article title.
 */
val FlowRowHorizontalPadding: Dp = 12.dp

/**
 * The width an article row reserves for the feed icon: the glyph plus the gap that follows it.
 *
 * The two halves are `FeedIcon`'s default 20dp and the 10dp `Spacer` next to it in the row's bottom
 * line. They are summed here rather than named separately because only the sum is ever compared
 * against anything - the row reserves 30dp in its top line, and the icon plus its gap fill exactly
 * that. If upstream ever changes either half, this constant is what has to move with it; the
 * upstream-sync checklist in `FORK-NOTES.md` names both numbers for that reason.
 */
val FlowIconReserve: Dp = 30.dp

/**
 * Where the text inside the flow list begins, measured from the left edge of the list.
 *
 * ## The chain this replaces
 *
 * Three files used to spell this distance out by hand, and only one of them was scaled for a
 * tablet:
 *
 * | caller | written as | Medium (1.15x) |
 * |---|---|---|
 * | `ArticleItem`, its two paddings | `adaptiveSize(12.dp)` twice | 27.6dp |
 * | `ArticleItem`, its two icon reserves | `adaptiveSize(30.dp)`, and a bare `30.dp` | 34.5dp / 30dp |
 * | `StickyHeader`, the date | bare `54.dp` / `24.dp` | 54dp / 24dp |
 * | `FlowPage`, the large title | bare `34.dp` / `8.dp` | 34dp / 8dp |
 *
 * The bare numbers are upstream's, and upstream gets away with them because upstream scales nothing:
 * on a phone every row above agrees on 24dp without an icon and 54dp with one. This fork scales the
 * row's padding (`adaptiveSize`), so the three of them stop agreeing on a tablet - and the
 * disagreement is not cosmetic. A date header that no longer sits over the article it dates, or a
 * title indented for an icon that is not drawn, both read as bugs rather than as a missing feature.
 *
 * So the distance is computed once, here, and the callers that only need to *match* the list -
 * the sticky header, the page title - read it instead of repeating it.
 *
 * ## Why the icon half is conditional
 *
 * The reserve is only there when the icon is drawn, and that decision has to be the same one the
 * rows make - see `shouldShowArticleFeedIcon`. A caller that works out "is there an icon" for
 * itself is exactly how the two halves drift apart, which is the failure this file exists to
 * prevent.
 *
 * ## Why the scale is a parameter
 *
 * So that the arithmetic runs on a plain JVM, where `FlowListInsetTest` pins it. `scaledDp` is
 * split out of `adaptiveSize` for the same reason.
 */
fun flowListTextInset(scale: Float, showFeedIcon: Boolean): Dp {
    val padding = scaledDp(FlowRowHorizontalPadding, scale)
    val icon = if (showFeedIcon) scaledDp(FlowIconReserve, scale) else 0.dp
    return padding + padding + icon
}

/** [flowListTextInset] for the current window. */
@Composable
fun flowListTextInset(showFeedIcon: Boolean): Dp = flowListTextInset(adaptiveScale(), showFeedIcon)

/**
 * How far a Material 3 two-row top app bar already places its title from the bar's own left edge,
 * before any padding the caller adds.
 *
 * A `LargeTopAppBar` composes its title twice: once in a 64dp row that also holds the navigation
 * icon and the actions, and once in the row below, which holds nothing else. Only the second one
 * draws the large title - the first draws the same text at `titleLarge`, which is the small title
 * that fades in as the bar collapses. That second row passes an *empty* navigation-icon slot, so the
 * placement collapses to the title inset itself: `TopAppBarTitleInset` (12dp, itself
 * `16.dp - TopAppBarHorizontalPadding`) plus the title slot's own `TopAppBarHorizontalPadding`
 * (4dp).
 *
 * Measured from material3 1.4.0 rather than guessed: `AppBar.kt` declares both as `private val`s and
 * applies them in `TopAppBarMeasurePolicy.placeTopAppBar` / `TopAppBarLayout`. Being private, they
 * are not part of the API and could move in a future release; the blast radius if they do is a
 * title that is off by a few dp, not a crash or a clipped layout, and
 * `FlowListInsetTest` fails first if the arithmetic here is edited carelessly.
 */
private val FlowTitleOriginInLargeTopAppBar: Dp = 16.dp

/**
 * The `start` padding the flow page's large title needs for its text to begin exactly where the
 * text in the list below it begins.
 *
 * [gutter] is `RYScaffold`'s centred-content gutter. The list receives it as padding on the
 * scaffold's content slot; the top app bar does not, because it is an explicit `topBar` and
 * shrinking it would take away the tap-anywhere-to-scroll-to-top target - see `RYScaffold`. Adding
 * it here is what keeps the two on the same column instead of the title hugging the window edge
 * while the list sits 80dp in on a tablet.
 *
 * Clamped at zero because Compose's `padding` is not a place to hand a negative dp: below
 * [FlowTitleOriginInLargeTopAppBar] the correct answer is "no extra padding", not "reach back
 * under the app bar's own inset".
 */
internal fun flowTitleStartPadding(gutter: Dp, textInset: Dp): Dp =
    (gutter + textInset - FlowTitleOriginInLargeTopAppBar).coerceAtLeast(0.dp)

/** [flowTitleStartPadding], reading the list's inset from the icon decision the rows make. */
@Composable
fun flowTitleStartPadding(gutter: Dp, showFeedIcon: Boolean): Dp =
    flowTitleStartPadding(gutter, flowListTextInset(showFeedIcon))
