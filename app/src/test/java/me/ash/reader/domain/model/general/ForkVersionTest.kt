package me.ash.reader.domain.model.general

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkVersionTest {

    @Test
    fun forkTagPutsTabletSerialInThePointField() {
        assertEquals("0.16.26", "v0.16.2-tablet.26".toForkVersion().toString())
        assertEquals("0.16.27", "v0.16.2-tablet.27".toForkVersion().toString())
    }

    @Test
    fun successiveBuildsAreOrdered() {
        assertTrue("v0.16.2-tablet.27".toForkVersion() > "v0.16.2-tablet.26".toForkVersion())
        assertTrue("v0.16.2-tablet.9".toForkVersion() < "v0.16.2-tablet.10".toForkVersion())
    }

    @Test
    fun forkBuildsCompareGreaterThanTheInstalledVersionName() {
        val installed = "0.16.2".toVersion()
        assertTrue("v0.16.2-tablet.26".toForkVersion() > installed)
    }

    @Test
    fun plainUpstreamTagsAreUnchanged() {
        assertEquals("0.16.3", "0.16.3".toForkVersion().toString())
        assertTrue("0.16.3".toForkVersion() > "0.16.2".toVersion())
    }

    @Test
    fun unknownShapesFallBackToTheUpstreamParser() {
        assertEquals("0.16.0", "v0.16.2-tablet".toForkVersion().toString())
        assertEquals("0.0.0", "not-a-tag".toForkVersion().toString())
    }

    @Test
    fun nullBlankAndPaddedTagsAreSafe() {
        val absent: String? = null
        assertEquals("0.0.0", absent.toForkVersion().toString())
        assertEquals("0.0.0", "".toForkVersion().toString())
        assertEquals("0.16.26", "  v0.16.2-tablet.26  ".toForkVersion().toString())
    }
}
