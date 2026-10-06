package me.ash.reader.ui.page.home.flow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the rule that drops the feed name above an article when the top bar already carries it.
 *
 * The feature is two booleans, so the risk is not that it is hard to write - it is that both halves
 * are *silent* when got wrong:
 *
 *  - Forget `!singleFeedFlow` and nothing changes at all. The name keeps repeating in exactly the
 *    view the user complained about, and the code still looks like it does something.
 *  - Forget `preferenceEnabled` and the "feed names" switch becomes dead in the multi-source views,
 *    which is a real regression: "All" and folder pages would lose the only label saying which feed
 *    an article came from, and the switch would no longer be able to bring it back.
 *
 * The subtler half is [isSingleFeedFlow], and specifically its `!groupScoped`. A group page that
 * also has a feed selected is still titled with the *folder* name, so the row must keep its label
 * there. That is the one input a plausible-looking rewrite (`feedScoped` alone) gets wrong, and it
 * is the input that quietly deletes information rather than merely repeating it.
 *
 * The feed icon obeys the same rule, and its mistakes cost more. The icon is not self-contained:
 * three other measurements reserve room for it - two in the row's top line, one in the sticky date
 * header - so a wrong answer here does not merely draw or hide a 20dp image, it leaves a 30dp gap
 * in front of every title. That is why the rule is a function called from all three places instead
 * of the same expression spelled out three times.
 *
 * All of these are plain functions over primitives rather than `@Composable`s, so they run on a
 * plain JVM. That is the reason they exist as separate functions at all - the same split as
 * `shouldLeaveAfterMarkAsRead` and `scaledDp`.
 */
class ArticleFeedNameTest {

    @Test
    fun `a single feed stops repeating its own name above every article`() {
        // The whole point of the change: the top bar says "少数派", so the rows must not.
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = true, singleFeedFlow = true))
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = false, singleFeedFlow = true))
    }

    @Test
    fun `a multi-source view keeps naming the feed, so All and folders are untouched`() {
        // Zero-regression case. "All", a folder, starred and search results are all named after the
        // collection, not the feed, so the row is the only label an article has.
        assertTrue(shouldShowArticleFeedName(preferenceEnabled = true, singleFeedFlow = false))
    }

    @Test
    fun `the switch still turns the name off everywhere`() {
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = false, singleFeedFlow = false))
    }

    @Test
    fun `every input combination is accounted for`() {
        // Exhaustive over the two booleans, written out rather than looped so that a dropped guard
        // shows up as a named failing row instead of an index in a message.
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = false, singleFeedFlow = false))
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = false, singleFeedFlow = true))
        assertTrue(shouldShowArticleFeedName(preferenceEnabled = true, singleFeedFlow = false))
        assertFalse(shouldShowArticleFeedName(preferenceEnabled = true, singleFeedFlow = true))
    }

    @Test
    fun `only a feed on its own counts as a single-feed flow`() {
        assertTrue(isSingleFeedFlow(groupScoped = false, feedScoped = true))
        assertFalse(isSingleFeedFlow(groupScoped = false, feedScoped = false))
        assertFalse(isSingleFeedFlow(groupScoped = true, feedScoped = false))
    }

    @Test
    fun `a group beats a feed, matching the title's own precedence`() {
        // The case a `feedScoped`-only rewrite gets wrong. The title would read the folder name, so
        // the rows have to keep their labels - dropping them here loses information silently.
        assertFalse(isSingleFeedFlow(groupScoped = true, feedScoped = true))
    }

    @Test
    fun `the decision is an and over two independent guards`() {
        // Stated as a property so that reordering cannot change the result, and so that a future
        // `||` or a stray `!` is caught even if the table above is edited carelessly.
        val inputs = listOf(false, true)
        for (preference in inputs) {
            for (single in inputs) {
                val expected = preference && !single
                assertTrue(
                    "preference=$preference single=$single",
                    shouldShowArticleFeedName(preference, single) == expected,
                )
            }
        }
    }

    // --- the feed icon, which shares the rule --------------------------------------------------

    @Test
    fun `a single feed stops drawing the same icon on every article`() {
        // Every row in a single-feed flow would draw the identical icon, so it identifies nothing
        // that the top bar has not already said.
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = true, singleFeedFlow = true))
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = false, singleFeedFlow = true))
    }

    @Test
    fun `a multi-source view keeps the icon, so All and folders are untouched`() {
        assertTrue(shouldShowArticleFeedIcon(preferenceEnabled = true, singleFeedFlow = false))
    }

    @Test
    fun `the feed-icon switch still turns the icon off everywhere`() {
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = false, singleFeedFlow = false))
    }

    @Test
    fun `every input combination is accounted for, for the icon too`() {
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = false, singleFeedFlow = false))
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = false, singleFeedFlow = true))
        assertTrue(shouldShowArticleFeedIcon(preferenceEnabled = true, singleFeedFlow = false))
        assertFalse(shouldShowArticleFeedIcon(preferenceEnabled = true, singleFeedFlow = true))
    }

    @Test
    fun `the icon is never drawn in a single-feed flow, whatever the switch says`() {
        // The requirement stated over the one input that must not matter. A rewrite to
        // `preferenceEnabled` alone passes every multi-source case above and fails only here - and
        // it fails on the user's device as a 30dp gap in front of each title, not as a missing icon.
        for (preference in listOf(false, true)) {
            assertFalse(
                "preference=$preference",
                shouldShowArticleFeedIcon(preferenceEnabled = preference, singleFeedFlow = true),
            )
        }
    }

    @Test
    fun `a single-feed flow silences the name and the icon together`() {
        // The two are separate switches and may differ in a multi-source view, but a single-feed
        // flow has to drop both. Pinned as a pair because the layout is what breaks if they drift:
        // the row reserves space for whichever of them is still on.
        for (preference in listOf(false, true)) {
            assertFalse(shouldShowArticleFeedName(preference, singleFeedFlow = true))
            assertFalse(shouldShowArticleFeedIcon(preference, singleFeedFlow = true))
        }
    }
}
