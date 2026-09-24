package me.ash.reader.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import me.ash.reader.infrastructure.preference.UiTextScalePreference
import me.ash.reader.infrastructure.preference.UiTextScalePreference.coerceToRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume
import org.junit.Test

/**
 * Pins the two properties that let the interface text size be a fork-safe setting.
 *
 *  - **100% is an identity transform**, and an *exact* one: the typography comes back as the same
 *    instance, not a copy holding equal numbers. That is what keeps a phone that never opens this
 *    setting byte-identical to upstream, and nothing else in the app asserts it - the default
 *    moving to 115 would compile, ship, and silently change every phone build.
 *  - **No slot is left behind.** `Typography.copy` fills omitted slots from Material 3's defaults
 *    rather than from the receiver, so a slot missing from `scaledTypography` would snap back to
 *    the stock size at 150% instead of growing. Sampling the four corners of the scale (display,
 *    title, body, label) catches a dropped line.
 *
 * The typography assertions skip themselves if Compose typography cannot be loaded off-device, the
 * same way `ListFontBaselineTest` does; a skip means the guard is inactive here, not that the
 * coupling is fine.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class UiTextScaleTest {

    @Test
    fun `the default is an exact identity`() {
        assertEquals(1.0f, uiTextScaleFactor(UiTextScalePreference.default), 0f)
    }

    @Test
    fun `an unscaled typography is handed back as the same instance`() {
        val base = loadOrSkip { Typography() }
        assertSame(base, scaledTypography(base, 1f))
    }

    @Test
    fun `every level of the hierarchy grows, so none of them keeps its old size`() {
        val base = loadOrSkip { Typography() }
        val scaled = scaledTypography(base, 1.5f)

        // The four levels the settings pages actually draw: DisplayText headings (displaySmall),
        // SettingItem titles (titleLarge), descriptions (bodyMedium), SubTitle/Tips (labelLarge).
        assertScaled(1.5f, base.displaySmall.fontSize.value, scaled.displaySmall.fontSize.value)
        assertScaled(1.5f, base.titleLarge.fontSize.value, scaled.titleLarge.fontSize.value)
        assertScaled(1.5f, base.bodyMedium.fontSize.value, scaled.bodyMedium.fontSize.value)
        assertScaled(1.5f, base.labelLarge.fontSize.value, scaled.labelLarge.fontSize.value)
        assertScaled(1.5f, base.labelSmall.fontSize.value, scaled.labelSmall.fontSize.value)
    }

    @Test
    fun `the line height grows with the size, so rows do not clip their own text`() {
        val base = loadOrSkip { Typography() }
        val scaled = scaledTypography(base, 1.5f)

        assertScaled(1.5f, base.bodyMedium.lineHeight.value, scaled.bodyMedium.lineHeight.value)
        assertScaled(1.5f, base.titleLarge.lineHeight.value, scaled.titleLarge.lineHeight.value)
    }

    @Test
    fun `the emphasized slots grow too, so a dropped line cannot hide behind the plain ones`() {
        // These are the slots `scaledTypography` is most likely to lose: they are the ones
        // `Type.kt`'s `applyFontFamily` walks last, and `Typography.copy` would silently fill a
        // forgotten one from Material 3's defaults.
        val base = loadOrSkip { Typography() }
        val scaled = scaledTypography(base, 1.5f)

        assertScaled(
            1.5f,
            base.displaySmallEmphasized.fontSize.value,
            scaled.displaySmallEmphasized.fontSize.value,
        )
        assertScaled(
            1.5f,
            base.titleLargeEmphasized.fontSize.value,
            scaled.titleLargeEmphasized.fontSize.value,
        )
        assertScaled(
            1.5f,
            base.labelSmallEmphasized.fontSize.value,
            scaled.labelSmallEmphasized.fontSize.value,
        )
    }

    @Test
    fun `the range keeps the stock appearance reachable and bounds the top end`() {
        assertEquals(100, UiTextScalePreference.default)
        assertEquals(UiTextScalePreference.default, UiTextScalePreference.min)
        assertEquals(100, UiTextScalePreference.min)
        assertEquals(150, UiTextScalePreference.max)
        assertEquals(UiTextScalePreference.min, 90.coerceToRange())
        assertEquals(UiTextScalePreference.max, 400.coerceToRange())
        assertEquals(115, 115.coerceToRange())
    }

    private fun assertScaled(scale: Float, expected: Float, actual: Float) {
        assertEquals((expected * scale).toDouble(), actual.toDouble(), 1e-3)
    }

    private fun <T> loadOrSkip(block: () -> T): T =
        try {
            block()
        } catch (e: Throwable) {
            Assume.assumeNoException(
                "Compose typography is not loadable on a plain JVM, so this guard is inactive here",
                e,
            )
            throw e
        }
}
