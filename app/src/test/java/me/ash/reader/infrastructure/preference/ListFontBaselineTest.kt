package me.ash.reader.infrastructure.preference

import androidx.compose.material3.Typography
import me.ash.reader.ui.theme.SystemTypography
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test

/**
 * Guards the one number that ties the adjustable list font size to the stock appearance.
 *
 * `ListFonts` turns the preference into a multiplier: `sizeSp / baseline`. The baseline is 16, and
 * 16 is not chosen - it is what `MaterialTheme.typography.titleMedium` happens to be, because the
 * group name (`ui/page/home/feeds/GroupItem.kt`) and the article title
 * (`ui/page/home/flow/ArticleItem.kt`) are both drawn in it, and the app overrides only
 * `bodySmallEmphasized` of Material 3's typography (`SystemTypography` in `ui/theme/Type.kt`).
 *
 * That makes the baseline an *implicit* dependency on a Material 3 default. If a Compose BOM bump
 * moved it, every list would render at `sizeSp / 16` of a style that is no longer 16sp - a silent
 * size change with no compile error and no other failing test. These assertions are the alarm:
 * they turn "upstream changed something that invalidates my fork" from a thing noticed on a device
 * into a red test.
 *
 * Both guards skip themselves if the Compose typography types cannot be loaded off-device, and a
 * skip means the guard is inactive here rather than that the coupling is fine.
 */
class ListFontBaselineTest {

    @Test
    fun `material 3 titleMedium is still the size both baselines assume`() {
        val titleMediumSp = loadOrSkip { Typography().titleMedium.fontSize.value.toDouble() }
        assertEquals(FeedsTextFontSizePreference.baseline.toDouble(), titleMediumSp, 1e-4)
        assertEquals(FlowTextFontSizePreference.baseline.toDouble(), titleMediumSp, 1e-4)
    }

    @Test
    fun `the app typography still leaves titleMedium at that size`() {
        val titleMediumSp = loadOrSkip { SystemTypography.titleMedium.fontSize.value.toDouble() }
        assertEquals(FeedsTextFontSizePreference.baseline.toDouble(), titleMediumSp, 1e-4)
        assertEquals(FlowTextFontSizePreference.baseline.toDouble(), titleMediumSp, 1e-4)
    }

    @Test
    fun `both baselines agree, so the two lists share one stock look`() {
        assertEquals(FeedsTextFontSizePreference.baseline, FlowTextFontSizePreference.baseline)
        assertEquals(FeedsTextFontSizePreference.default, FeedsTextFontSizePreference.baseline)
        assertEquals(FlowTextFontSizePreference.default, FlowTextFontSizePreference.baseline)
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
