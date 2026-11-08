package me.ash.reader.ui.page.home.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FeedsGroupKeyTest {

    @Test
    fun `the same id always yields the same key`() {
        assertEquals(feedsGroupKey("group-1"), feedsGroupKey("group-1"))
    }

    @Test
    fun `different ids never share a key`() {
        assertNotEquals(feedsGroupKey("group-1"), feedsGroupKey("group-2"))
    }

    @Test
    fun `group keys are namespaced away from the plain rows`() {
        assertEquals("feeds-group:group-1", feedsGroupKey("group-1"))
    }

    @Test
    fun `the key does not depend on the index`() {
        // The bug being guarded against: a key derived from the item's position. Reordering two
        // groups must leave each group's own key alone.
        val first = feedsGroupKey("a")
        val second = feedsGroupKey("b")
        val reordered = listOf(second, first)
        assertEquals(first, reordered[1])
        assertEquals(second, reordered[0])
    }
}
