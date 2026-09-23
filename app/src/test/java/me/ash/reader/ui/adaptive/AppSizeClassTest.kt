package me.ash.reader.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The size class decides whether the layout adapts at all, so its breakpoints are the one place
 * where a silent mistake reaches every screen. Nothing else in the app asserts them.
 */
class AppSizeClassTest {

    @Test
    fun `breakpoints follow the material window size classes`() {
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(320))
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(599))
        assertEquals(AppSizeClass.Medium, AppSizeClass.fromWidthDp(600))
        assertEquals(AppSizeClass.Medium, AppSizeClass.fromWidthDp(839))
        assertEquals(AppSizeClass.Expanded, AppSizeClass.fromWidthDp(840))
        assertEquals(AppSizeClass.Expanded, AppSizeClass.fromWidthDp(1280))
    }

    @Test
    fun `an unknown width resolves to Compact so nothing adapts on first composition`() {
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(0))
    }

    @Test
    fun `a negative width resolves to Compact instead of throwing`() {
        assertEquals(AppSizeClass.Compact, AppSizeClass.fromWidthDp(-1))
    }

    @Test
    fun `every entry is reachable, so no bucket is dead`() {
        val reached = listOf(0, 600, 840).map { AppSizeClass.fromWidthDp(it) }.toSet()
        assertEquals(AppSizeClass.entries.toSet(), reached)
    }
}
