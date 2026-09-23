package me.ash.reader.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gutter arithmetic decides how much of a wide window is given back as margin. It is a pure
 * function precisely so that these cases can be pinned down without a device: the two that matter
 * most are "a pane must not be inset" (which is what makes a window-derived width unusable) and
 * "an unknown width must not adapt" (which is what keeps the first composition stable).
 */
class AdaptiveContentWidthTest {

    @Test
    fun `widths up to the cap are left completely alone`() {
        assertGutter(0f, 0.dp)
        assertGutter(0f, 360.dp)
        assertGutter(0f, AdaptiveContentMaxWidth)
    }

    @Test
    fun `a list-detail pane collapses to zero, so the list column is never squeezed`() {
        // The list pane of the pane scaffold is far narrower than the cap on every window size.
        assertGutter(0f, 360.dp)
        assertGutter(0f, 600.dp)
    }

    @Test
    fun `width beyond the cap becomes a symmetric gutter`() {
        assertGutter(80f, 800.dp)
        assertGutter(320f, 1280.dp)
    }

    @Test
    fun `an odd remainder is still centred`() {
        assertGutter(80.5f, 801.dp)
    }

    @Test
    fun `an unspecified or unbounded width does not adapt`() {
        assertGutter(0f, Dp.Unspecified)
        assertGutter(0f, Dp.Infinity)
    }

    private fun assertGutter(expectedDp: Float, availableWidth: Dp) {
        val actual = adaptiveContentGutter(availableWidth, AdaptiveContentMaxWidth).value
        assertEquals(expectedDp.toDouble(), actual.toDouble(), 1e-4)
    }
}
