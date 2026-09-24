package me.ash.reader.ui.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the multiplier the whole tablet adaptation rests on.
 *
 * Two of these assertions are load-bearing rather than merely descriptive:
 *
 *  - **Compact is exactly `1f`.** That is the property that makes this layer acceptable as a fork:
 *    a phone cannot render differently from upstream, so there is nothing for a reviewer to weigh.
 *    Nothing else in the app asserts it, and `1.05f` would compile, ship, and change every phone
 *    build silently.
 *  - **Scaling never moves the content width cap.** The gutter and the multiplier are independent
 *    axes; folding one into the other would quietly invalidate `AdaptiveContentWidthTest`, which
 *    assumes the cap is a fixed 640dp.
 *
 * `adaptiveSize` itself is `@Composable` and cannot run on a plain JVM, so the arithmetic lives in
 * the pure [scaledDp] for exactly this reason - the same split as `adaptiveContentGutter` and
 * `rememberAdaptiveContentGutter`.
 */
class AdaptiveScaleTest {

    @Test
    fun `compact is an identity transform, so a phone still matches upstream`() {
        assertEquals(1.0, AdaptiveScaleCompact.toDouble(), 1e-4)
        assertEquals(1.0, AdaptiveLayoutSpec.Compact.scale.toDouble(), 1e-4)
    }

    @Test
    fun `an unscaled dimension is returned unchanged`() {
        assertEquals(24.dp, scaledDp(24.dp, AdaptiveScaleCompact))
        assertEquals(64.dp, scaledDp(64.dp, AdaptiveScaleCompact))
        assertEquals(172.dp, scaledDp(172.dp, AdaptiveScaleCompact))
    }

    @Test
    fun `a scaled dimension is the exact product`() {
        // The base values the pages actually pass, so changing any of them shows up here first.
        assertScaled(23f, 20.dp, AdaptiveScaleMedium) // feed logo / gear
        assertScaled(25f, 20.dp, AdaptiveScaleExpanded)
        assertScaled(27.6f, 24.dp, AdaptiveScaleMedium) // filter bar glyph
        assertScaled(30f, 24.dp, AdaptiveScaleExpanded)
        assertScaled(73.6f, 64.dp, AdaptiveScaleMedium) // filter bar height, icon style
        assertScaled(80f, 64.dp, AdaptiveScaleExpanded)
        assertScaled(92f, 80.dp, AdaptiveScaleMedium) // filter bar height, label style
        assertScaled(100f, 80.dp, AdaptiveScaleExpanded)
        assertScaled(197.8f, 172.dp, AdaptiveScaleMedium) // flow top bar
        assertScaled(215f, 172.dp, AdaptiveScaleExpanded)
    }

    @Test
    fun `the size classes scale monotonically and stay mild`() {
        assertTrue(AdaptiveScaleCompact < AdaptiveScaleMedium)
        assertTrue(AdaptiveScaleMedium < AdaptiveScaleExpanded)
        // Past roughly 1.3 the glyphs start to crowd the fixed 40dp IconButton container they sit
        // in, so this is a design limit, not an arbitrary ceiling.
        assertTrue("scale ${AdaptiveScaleExpanded} is no longer mild", AdaptiveScaleExpanded <= 1.3f)
    }

    @Test
    fun `every size class resolves to its own spec`() {
        assertEquals(AdaptiveLayoutSpec.Compact, AdaptiveLayoutSpec.of(AppSizeClass.Compact))
        assertEquals(AdaptiveLayoutSpec.Medium, AdaptiveLayoutSpec.of(AppSizeClass.Medium))
        assertEquals(AdaptiveLayoutSpec.Expanded, AdaptiveLayoutSpec.of(AppSizeClass.Expanded))
    }

    @Test
    fun `an 8_8 inch tablet is covered in both orientations`() {
        // Portrait lands in Medium (~800dp), landscape in Expanded (~1280dp). Medium carrying a
        // scale is the point: skipping it would make the adaptation vanish in the orientation this
        // app is most often read in.
        assertEquals(AppSizeClass.Medium, AppSizeClass.fromWidthDp(800))
        assertEquals(AdaptiveScaleMedium, AdaptiveLayoutSpec.of(AppSizeClass.fromWidthDp(800)).scale)
        assertEquals(
            AdaptiveScaleExpanded,
            AdaptiveLayoutSpec.of(AppSizeClass.fromWidthDp(1280)).scale,
        )
    }

    @Test
    fun `scaling never moves the content width cap, so the two axes stay independent`() {
        for (spec in allSpecs()) {
            assertEquals(AdaptiveContentMaxWidth, spec.contentMaxWidth)
        }
    }

    private fun allSpecs(): List<AdaptiveLayoutSpec> =
        AppSizeClass.entries.map { AdaptiveLayoutSpec.of(it) }

    private fun assertScaled(expectedDp: Float, base: Dp, scale: Float) {
        assertEquals(expectedDp.toDouble(), scaledDp(base, scale).value.toDouble(), 1e-3)
    }
}
