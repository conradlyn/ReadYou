package me.ash.reader.domain.service

import me.ash.reader.infrastructure.net.LatestRelease
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ForkUpdateSourceTest {

    private fun release(tag: String) = LatestRelease(tag_name = tag)

    @Test
    fun newerPreviewWinsOverOlderStableRelease() {
        val stable = release("v0.16.2-tablet.26")
        val preview = release("v0.16.2-tablet.27")
        assertSame(preview, newerRelease(stable, preview))
    }

    @Test
    fun stableWinsWhenItIsNewer() {
        val stable = release("v0.16.2-tablet.28")
        val preview = release("v0.16.2-tablet.27")
        assertSame(stable, newerRelease(stable, preview))
    }

    @Test
    fun equalTagsKeepTheStableRelease() {
        val stable = release("v0.16.2-tablet.26")
        val preview = release("v0.16.2-tablet.26")
        assertSame(stable, newerRelease(stable, preview))
    }

    @Test
    fun missingStableFallsBackToTheList() {
        val preview = release("v0.16.2-tablet.27")
        assertSame(preview, newerRelease(null, preview))
    }

    @Test
    fun missingListFallsBackToTheStableRelease() {
        val stable = release("v0.16.2-tablet.26")
        assertSame(stable, newerRelease(stable, null))
    }

    @Test
    fun bothMissingIsNull() {
        assertNull(newerRelease(null, null))
    }

    @Test
    fun plainUpstreamTagsStillCompare() {
        val stable = release("0.16.2")
        val preview = release("0.16.3")
        assertSame(preview, newerRelease(stable, preview))
    }

    // GitHub's /releases list pins the newest stable release at the head, so the newest preview
    // is usually the *second* entry. Picking firstOrNull() would never surface it.
    @Test
    fun newestByVersionIgnoresThePinnedStableReleaseHead() {
        val stable = release("v0.16.2-tablet.26")
        val preview = release("v0.16.2-tablet.27")
        val older = release("v0.16.2-tablet.25")
        assertSame(preview, newestByVersion(listOf(stable, preview, older)))
        assertSame(preview, newerRelease(stable, newestByVersion(listOf(stable, preview, older))))
    }

    @Test
    fun newestByVersionKeepsTheEarlierEntryOnTies() {
        val stable = release("v0.16.2-tablet.26")
        val duplicate = release("v0.16.2-tablet.26")
        assertSame(stable, newestByVersion(listOf(stable, duplicate)))
    }

    @Test
    fun newestByVersionHandlesEmptyAndNull() {
        assertNull(newestByVersion(null))
        assertNull(newestByVersion(emptyList()))
    }

    @Test
    fun newestByVersionComparesPlainUpstreamTags() {
        val older = release("0.16.2")
        val newer = release("0.16.3")
        assertSame(newer, newestByVersion(listOf(older, newer)))
    }
}
