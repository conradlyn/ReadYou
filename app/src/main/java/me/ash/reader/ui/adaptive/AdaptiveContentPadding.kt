package me.ash.reader.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The width that actually reaches the current page, in dp.
 *
 * Distinct from [rememberWindowWidthDp] on purpose. That one reports the *window*, which is the
 * right input for a size class but the wrong input for a gutter: inside the list pane of the
 * list-detail layout (`ui/page/adaptive/ArticleListReadingPage.kt`) the pane is narrower than the
 * window, and a gutter derived from the window width would squeeze a column that has no room to
 * give. Reading the incoming constraint instead makes the gutter collapse to zero there for free,
 * which is what keeps the gutters correct inside a pane and outside it with one rule.
 *
 * Provided by `RYScaffold` (`ui/component/base/RYScaffold.kt`) from a `BoxWithConstraints` wrapped
 * around the whole scaffold, so it is in scope for the `content` and the `bottomBar` slots alike.
 *
 * Defaults to [Dp.Unspecified], which every reader here treats as "do not adapt". A composable that
 * reads this without a `RYScaffold` above it therefore gets no gutter rather than a guess.
 */
val LocalAvailableWidthDp = compositionLocalOf { Dp.Unspecified }

/**
 * The current window width in dp.
 *
 * Uses [LocalWindowInfo] rather than `LocalConfiguration.screenWidthDp`, because the latter reports
 * the *screen*: in split-screen or a freeform desktop window it would report a width the window
 * does not have. Returns 0 while the container size is still unknown (first composition), which
 * [AppSizeClass.fromWidthDp] maps to [AppSizeClass.Compact], i.e. "do not adapt yet".
 */
@Composable
fun rememberWindowWidthDp(): Int {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(containerSize, density.density) {
        if (containerSize.width > 0) {
            with(density) { containerSize.width.toDp().value.toInt() }
        } else {
            0
        }
    }
}

/**
 * Half of the space that must be left empty on each side for a column no wider than
 * [maxContentWidth] to end up centred in [availableWidth].
 *
 * A pure function, so the arithmetic can be unit tested without a composition - see
 * `app/src/test/java/me/ash/reader/ui/adaptive/AdaptiveContentWidthTest.kt`. A non-finite width (an
 * unmeasured [Dp.Unspecified] default, or an unbounded parent) yields `0.dp` rather than following
 * the arithmetic to infinity.
 */
internal fun adaptiveContentGutter(availableWidth: Dp, maxContentWidth: Dp): Dp {
    if (!availableWidth.value.isFinite() || !maxContentWidth.value.isFinite()) return 0.dp
    return ((availableWidth.value - maxContentWidth.value) / 2f).coerceAtLeast(0f).dp
}

/**
 * The gutter for a width the caller has already measured.
 *
 * Exists so that `RYScaffold` - which is where the width gets measured - can apply the same gutter
 * that its descendants will compute, without having to publish the width to itself first.
 */
@Composable
internal fun rememberAdaptiveContentGutter(availableWidth: Dp): Dp {
    val spec = LocalAdaptiveLayout.current
    return remember(spec, availableWidth) {
        // A compact window must stay byte-identical to upstream, so skip the arithmetic entirely.
        // This is also what makes AppSizeClass a real input to the layout rather than a value that
        // is computed and never read.
        if (spec.sizeClass == AppSizeClass.Compact) 0.dp
        else adaptiveContentGutter(availableWidth, spec.contentMaxWidth)
    }
}

/**
 * The gutter implied by the width published by the nearest `RYScaffold`, in dp.
 *
 * Use this for the few surfaces that need to align with the content column but cannot simply be
 * padded - a top app bar's icons, or a bottom bar that draws its own full-bleed background. For
 * everything that is ordinary content, `RYScaffold` has already applied it.
 */
@Composable
fun rememberAdaptiveContentGutter(): Dp = rememberAdaptiveContentGutter(LocalAvailableWidthDp.current)

/**
 * Symmetric horizontal padding that caps content at [AdaptiveLayoutSpec.contentMaxWidth] and
 * centres it. On a compact window, and anywhere the available width is already narrower than the
 * cap (a list-detail pane, for instance), this resolves to exactly `0.dp`, so those layouts stay
 * byte-identical to upstream.
 *
 * Kept as a `PaddingValues` seam for full-width `LazyColumn`s and for `FilterBar`, which needs the
 * inset on its items while its own surface stays full-bleed: passing
 * `contentPadding = rememberAdaptiveContentPadding()` is a one-line change that needs no
 * re-indentation of the surrounding block. That matters more than it sounds — a re-indented block
 * shows up in `git diff` as dozens of changed lines, and dozens of changed lines in a file upstream
 * also edits is exactly how a rebase turns into a rewrite.
 *
 * @param vertical optional vertical padding, for lists that need breathing room at both ends.
 */
@Composable
fun rememberAdaptiveContentPadding(vertical: Dp = 0.dp): PaddingValues {
    val horizontal = rememberAdaptiveContentGutter()
    return remember(horizontal, vertical) {
        PaddingValues(horizontal = horizontal, vertical = vertical)
    }
}
