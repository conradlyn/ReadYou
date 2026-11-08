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
}
