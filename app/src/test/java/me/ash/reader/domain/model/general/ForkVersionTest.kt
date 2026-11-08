package me.ash.reader.domain.model.general

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkVersionTest {

    @Test
    fun forkTagPutsThePointAndTabletSerialInThePointField() {
        assertEquals("0.16.20026", "v0.16.2-tablet.26".toForkVersion().toString())
        assertEquals("0.16.20027", "v0.16.2-tablet.27".toForkVersion().toString())
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
        assertEquals("0.16.20026", "  v0.16.2-tablet.26  ".toForkVersion().toString())
    }

    @Test
    fun installedBuildKnowsItsOwnTabletSerial() {
        val installed = installedForkVersion("0.16.2", 28)
        assertEquals("0.16.20028", installed.toString())
        // The same build must NOT be announced again on every cold start.
        assertFalse("v0.16.2-tablet.28".toForkVersion().whetherNeedUpdate(installed, "".toVersion()))
        assertTrue("v0.16.2-tablet.29".toForkVersion().whetherNeedUpdate(installed, "".toVersion()))
    }

    // After rebasing an upstream versionName bump the serial restarts at 1. If the versionName
    // point were dropped from the mapping, `v0.16.3-tablet.1` would sort *below* tablet.29 and
    // the updater would go silent for the whole new version line.
    @Test
    fun aVersionNamePointBumpStillOutranksEveryOldTabletBuild() {
        val installed = installedForkVersion("0.16.2", 29)
        val firstBuildOfNextPoint = "v0.16.3-tablet.1".toForkVersion()
        assertEquals("0.16.30001", firstBuildOfNextPoint.toString())
        assertTrue(firstBuildOfNextPoint.whetherNeedUpdate(installed, "".toVersion()))

        val installedNextPoint = installedForkVersion("0.16.3", 1)
        // Version has no equals() override: compare its rendering, not object identity.
        assertEquals(firstBuildOfNextPoint.toString(), installedNextPoint.toString())
        assertFalse(firstBuildOfNextPoint.whetherNeedUpdate(installedNextPoint, "".toVersion()))
    }

    @Test
    fun aMinorVersionBumpStillOutranksEveryOldTabletBuild() {
        val installed = installedForkVersion("0.16.2", 29)
        assertTrue("v0.17.0-tablet.1".toForkVersion() > installed)
    }

    @Test
    fun localBuildsFallBackToTheVersionName() {
        val local = installedForkVersion("0.16.2", 0)
        assertEquals("0.16.2", local.toString())
        assertTrue("v0.16.2-tablet.28".toForkVersion() > local)
    }

    @Test
    fun installedVersionIsSafeForOddInput() {
        assertEquals("0.0.0", installedForkVersion(null, 0).toString())
        assertEquals("0.0.28", installedForkVersion("bogus", 28).toString())
    }

    @Test
    fun hugePointFieldsDoNotOverflow() {
        val enormous = "v999999999.0.999999999-tablet.5".toForkVersion()
        assertTrue(enormous > "0.16.2-tablet.29".toForkVersion())
    }

    @Test
    fun labelShowsTheTabletBuildOnlyForReleaseBuilds() {
        assertEquals("0.16.2-tablet.28", installedForkVersionLabel("0.16.2", 28))
        assertEquals("0.16.2", installedForkVersionLabel("0.16.2", 0))
        assertEquals("", installedForkVersionLabel(null, 0))
    }
}
