package me.ash.reader.ui.page.home.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * The reading page hides its toolbars while the reader scrolls down and shows them again on the
 * way back up. Upstream keeps that flag in a `mutableStateOf` read directly in `ReadingPage`, so
 * every change of direction recomposes the whole page - including `AnimatedContent`, `Content`
 * and the `Reader` that turns the article into lazy items.
 *
 * Holding the flag behind a [State] and letting only the two toolbars read its value pushes the
 * recomposition scope down to those bars: `ReadingPage` never reads it in its own body, and the
 * `Scaffold` content lambda only captures the stable [State] object, so the subtree that renders
 * the article is left alone. The visual rule is unchanged.
 */
@Stable
class ReaderScrollDirection internal constructor(
    private val scrollingDown: MutableState<Boolean>,
) {
    val isScrollingDown: Boolean
        get() = scrollingDown.value

    fun onScroll(delta: Float) {
        readerScrollDirectionOrNull(delta)?.let { scrollingDown.value = it }
    }
}

/**
 * Pure half of [ReaderScrollDirection.onScroll], kept out of the Compose layer so it can be
 * pinned by a plain JVM test. `null` means "too small to count" - the direction must not change.
 */
fun readerScrollDirectionOrNull(delta: Float): Boolean? =
    if (abs(delta) > ScrollDirectionThreshold) delta < 0f else null

private const val ScrollDirectionThreshold = 2f

@Composable
fun rememberReaderScrollDirection(): ReaderScrollDirection {
    val scrollingDown = remember { mutableStateOf(false) }
    return remember { ReaderScrollDirection(scrollingDown) }
}

@Composable
fun rememberReaderToolbarVisible(
    autoHideToolbar: Boolean,
    hasArticle: Boolean,
    scrollDirection: ReaderScrollDirection,
): State<Boolean> =
    remember(autoHideToolbar, hasArticle, scrollDirection) {
        derivedStateOf {
            if (autoHideToolbar) hasArticle && !scrollDirection.isScrollingDown else true
        }
    }
