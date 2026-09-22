package me.ash.reader.ui.adaptive

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The current window width in dp.
 *
 * Uses [LocalWindowInfo] rather than `LocalConfiguration.screenWidthDp`, because the latter reports
 * the *screen*: in split-screen or a freeform desktop window it would over-pad and push content off
 * centre. Returns 0 while the container size is still unknown (first composition), which callers
 * are expected to treat as "do not adapt yet".
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
 * Symmetric horizontal padding that caps content at [AdaptiveLayoutSpec.contentMaxWidth] and
 * centres it. On a compact window this resolves to exactly `0.dp`, so phone layouts stay
 * byte-identical to upstream.
 *
 * This is the cheapest available seam for a full-width `LazyColumn`: adding
 * `contentPadding = rememberAdaptiveContentPadding()` to the list is a one-line change that needs
 * no re-indentation of the surrounding block. That matters more than it sounds — a re-indented
 * block shows up in `git diff` as dozens of changed lines, and dozens of changed lines in a file
 * upstream also edits is exactly how a rebase turns into a rewrite.
 *
 * @param vertical optional vertical padding, for lists that need breathing room at both ends.
 */
@Composable
fun rememberAdaptiveContentPadding(vertical: Dp = 0.dp): PaddingValues {
    val maxContentWidth = LocalAdaptiveLayout.current.contentMaxWidth
    val widthDp = rememberWindowWidthDp()
    val horizontal = ((widthDp - maxContentWidth.value) / 2f).coerceAtLeast(0f).dp
    return remember(horizontal, vertical) {
        PaddingValues(horizontal = horizontal, vertical = vertical)
    }
}
