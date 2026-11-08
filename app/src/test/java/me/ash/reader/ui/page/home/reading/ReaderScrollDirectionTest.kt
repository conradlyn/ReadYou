package me.ash.reader.ui.page.home.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderScrollDirectionTest {

    @Test
    fun `a downward flick hides the toolbars`() {
        assertEquals(true, readerScrollDirectionOrNull(-10f))
    }

    @Test
    fun `an upward flick shows the toolbars`() {
        assertEquals(false, readerScrollDirectionOrNull(10f))
    }

    @Test
    fun `tiny jitters never flip the direction`() {
        assertNull(readerScrollDirectionOrNull(0f))
        assertNull(readerScrollDirectionOrNull(2f))
        assertNull(readerScrollDirectionOrNull(-2f))
        assertNull(readerScrollDirectionOrNull(1.9f))
        assertNull(readerScrollDirectionOrNull(-1.9f))
    }

    @Test
    fun `the old threshold is strictly greater than two`() {
        // Upstream used `abs(f) > 2f`; moving the logic must not widen or narrow it.
        assertNull(readerScrollDirectionOrNull(2.0f))
        assertEquals(true, readerScrollDirectionOrNull(-2.01f))
        assertEquals(false, readerScrollDirectionOrNull(2.01f))
    }
}
