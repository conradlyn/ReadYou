package me.ash.reader.ui.page.home.flow

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.ash.reader.ui.adaptive.scaledDp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the one distance that three separate widgets have to agree on: where the text in the flow
 * list begins.
 *
 * Every mistake this file guards against is invisible in review and obvious on a tablet:
 *
 *  - The sticky date header and the article rows drifting apart, so a date no longer sits over the
 *    article it dates. Upstream wrote both as literal dp, which is self-consistent until the fork
 *    scales the row's padding and leaves the header alone.
 *  - The page title indented for an icon that is not drawn, or indented by the wrong amount because
 *    upstream's `34.dp` does not actually equal the row's icon reserve.
 *  - The title hugging the window edge while the list below it is centred by `RYScaffold`'s gutter.
 *
 * The arithmetic is a plain function over primitives rather than a `@Composable` precisely so that
 * these cases can be stated here, without a device - the same split as `scaledDp` and
 * `adaptiveContentGutter`.
 */
class FlowListInsetTest {

    @Test
    fun `without an icon the text sits one row padding in from each side`() {
        // 12dp outside the card, 12dp inside it. Upstream's 24dp, and the reason its 8dp title
        // padding was the right number: 8 + the app bar's own 16dp origin = 24.
        assertInset(24f, scale = 1f, showFeedIcon = false)
        assertTitlePadding(8f, gutter = 0.dp, textInset = 24.dp)
    }

    @Test
    fun `the icon pushes the text out by exactly the icon reserve`() {
        // 24 + 30. Upstream's 54dp for the date header, and 50dp for the title - which is the bug
        // this file fixes: the reserve is 30dp, so the title's padding has to be 38dp, not 34dp.
        assertInset(54f, scale = 1f, showFeedIcon = true)
        assertTitlePadding(38f, gutter = 0.dp, textInset = 54.dp)
    }

    @Test
    fun `a tablet scales both halves, so neither the header nor the title drifts`() {
        // Medium is the size class of an 8.8" tablet held in portrait. A bare `24.dp` header would
        // stay at 24dp while the rows moved to 27.6dp; a bare `8.dp` title would stay at 24dp while
        // the rows moved too.
        assertInset(27.6f, scale = 1.15f, showFeedIcon = false)
        assertInset(62.1f, scale = 1.15f, showFeedIcon = true)
        assertInset(30f, scale = 1.25f, showFeedIcon = false)
        assertInset(67.5f, scale = 1.25f, showFeedIcon = true)
    }

    @Test
    fun `the reserve is the only difference between the two icon states`() {
        // Stated as a property rather than as a table, because this is the relation the sticky
        // header and the title depend on: they are given one inset, and the only thing that may
        // move it is the icon the rows themselves decided to draw.
        for (scale in listOf(1f, 1.15f, 1.25f)) {
            val withoutIcon = flowListTextInset(scale, showFeedIcon = false)
            val withIcon = flowListTextInset(scale, showFeedIcon = true)
            assertDp(scaledDp(FlowIconReserve, scale), withIcon - withoutIcon, "scale=$scale")
        }
    }

    @Test
    fun `the title's padding lands the title on the list's text, not on the window edge`() {
        // The title's own origin inside the app bar is 16dp, so the padding is the list's inset less
        // that origin - and the gutter has to be added, or the title stays at the window edge while
        // the list is centred 80dp in on a tablet.
        assertTitlePadding(8f, gutter = 0.dp, textInset = 24.dp)
        assertTitlePadding(91.6f, gutter = 80.dp, textInset = 27.6f.dp)
        assertTitlePadding(126.1f, gutter = 80.dp, textInset = 62.1f.dp)
    }

    @Test
    fun `the padding never goes negative, whatever the app bar's origin is`() {
        // A negative dp handed to `Modifier.padding` would pull the title back under the app bar's
        // own inset, over the navigation icon. "No extra padding" is the only sane floor.
        assertTitlePadding(0f, gutter = 0.dp, textInset = 0.dp)
        assertTitlePadding(0f, gutter = 4.dp, textInset = 8.dp)
    }

    private fun assertInset(expectedDp: Float, scale: Float, showFeedIcon: Boolean) {
        assertDp(
            expectedDp.dp,
            flowListTextInset(scale, showFeedIcon),
            "scale=$scale showFeedIcon=$showFeedIcon",
        )
    }

    private fun assertTitlePadding(expectedDp: Float, gutter: Dp, textInset: Dp) {
        assertDp(
            expectedDp.dp,
            flowTitleStartPadding(gutter, textInset),
            "gutter=$gutter textInset=$textInset",
        )
    }

    private fun assertDp(expected: Dp, actual: Dp, message: String) {
        assertEquals(message, expected.value.toDouble(), actual.value.toDouble(), 1e-4)
    }
}
