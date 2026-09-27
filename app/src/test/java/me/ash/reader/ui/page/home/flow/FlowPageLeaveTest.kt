package me.ash.reader.ui.page.home.flow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rule that turns "the list went empty" into "go back to the feeds list".
 *
 * The feature is one line of arithmetic, so the risk is not that it is hard to write - it is that
 * two of its three guards are *silent* when dropped. Each one alone still compiles, still ships, and
 * still passes a hand test of the happy path:
 *
 *  - Drop `armed` and every page that opens empty bounces the user straight back out. That is the
 *    feed you have already read to the end, and the search that found nothing - both are pages the
 *    user deliberately opened, and both would flash and disappear.
 *  - Drop `isIdle` and the reload that the write itself triggers reads as an empty feed. The count
 *    drops to 0 while the replacement page is still in flight, so the page would leave on the way
 *    *to* being repopulated, not on the way to being empty.
 *  - Drop `itemCount == 0` and the page navigates away on every mark-as-read, even when the filter
 *    is "All" and the articles are still there.
 *
 * So the assertions below are deliberately written as a table over all eight inputs rather than as
 * three happy-path cases: the four `false` rows are the ones a refactor is likely to break, and a
 * table makes "only this one combination leaves" the visible, checkable claim.
 *
 * `shouldLeaveAfterMarkAsRead` is a plain function over primitives, not a `@Composable`, so it runs
 * on a plain JVM. That is the reason it exists as a separate function at all - the same split as
 * `scaledDp` and `adaptiveContentGutter`.
 */
class FlowPageLeaveTest {

    @Test
    fun `only an armed, settled, emptied page hands itself back`() {
        // The single `true` cell: the action had articles to mark, the list has finished loading,
        // and it came back with nothing.
        assertTrue(shouldLeaveAfterMarkAsRead(armed = true, itemCount = 0, isIdle = true))
    }

    @Test
    fun `a page that was already empty never navigates away on its own`() {
        // Opening a fully-read feed, or searching for a term that matches nothing. Nothing was
        // marked, so nothing may be undone by bouncing the user out of the page they just opened.
        assertFalse(shouldLeaveAfterMarkAsRead(armed = false, itemCount = 0, isIdle = true))
    }

    @Test
    fun `a count that collapsed mid-reload is not an empty feed`() {
        // The write invalidates the paging source, so `itemCount` reads 0 while the next page is
        // still loading. Without `isIdle` this is the case that leaves too early.
        assertFalse(shouldLeaveAfterMarkAsRead(armed = true, itemCount = 0, isIdle = false))
    }

    @Test
    fun `an armed page that still has articles stays put`() {
        // The "All" filter: marking everything read does not remove it from the list. The flag is
        // still set, so only the count keeps the page from leaving.
        assertFalse(shouldLeaveAfterMarkAsRead(armed = true, itemCount = 1, isIdle = true))
        assertFalse(shouldLeaveAfterMarkAsRead(armed = true, itemCount = 50, isIdle = true))
    }

    @Test
    fun `every input other than the one that leaves is a no-op`() {
        // Exhaustive over the three booleans. Written out rather than looped so that a guard being
        // dropped shows up as a named failing row instead of an index in a message.
        assertFalse(shouldLeaveAfterMarkAsRead(armed = false, itemCount = 0, isIdle = false))
        assertFalse(shouldLeaveAfterMarkAsRead(armed = false, itemCount = 1, isIdle = true))
        assertFalse(shouldLeaveAfterMarkAsRead(armed = false, itemCount = 1, isIdle = false))
        assertFalse(shouldLeaveAfterMarkAsRead(armed = true, itemCount = 1, isIdle = false))
    }

    @Test
    fun `the decision is an and, so no single guard can carry it alone`() {
        // Stated as a property so that reordering the conjunction cannot change the result, and so
        // that a future `||` or a stray `!` is caught even if the table above is edited carelessly.
        val inputs = listOf(false, true)
        for (armed in inputs) {
            for (idle in inputs) {
                for (count in listOf(0, 1, 50)) {
                    val expected = armed && idle && count == 0
                    assertTrue(
                        "armed=$armed idle=$idle count=$count",
                        shouldLeaveAfterMarkAsRead(armed, count, idle) == expected,
                    )
                }
            }
        }
    }
}
