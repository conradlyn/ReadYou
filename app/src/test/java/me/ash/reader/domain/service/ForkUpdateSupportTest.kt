package me.ash.reader.domain.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForkUpdateSupportTest {

    private val ownAsset =
        "https://github.com/conradlyn/ReadYou/releases/download/" +
            "v0.16.2-tablet.28/ReadYou-0.16.2-a4c4267c.apk"

    @Test
    fun acceptsAnOwnReleaseAsset() {
        assertTrue(isForkApkDownload(ownAsset))
        assertTrue(isForkApkDownload(ownAsset.replace("github.com", "github.com:443")))
    }

    @Test
    fun rejectsOtherHostsSchemesAndRepositories() {
        assertFalse(isForkApkDownload(ownAsset.replace("https://", "http://")))
        assertFalse(isForkApkDownload(ownAsset.replace("github.com", "github.com.evil.com")))
        assertFalse(isForkApkDownload(ownAsset.replace("conradlyn/ReadYou", "someone/else")))
        assertFalse(isForkApkDownload("https://example.com/readyou.apk"))
        assertFalse(isForkApkDownload(ownAsset.replace("github.com", "github.com:8443")))
        assertFalse(isForkApkDownload(ownAsset.replace("https://", "https://evil@")))
    }

    @Test
    fun rejectsNonApkTargetsQueriesFragmentsAndTraversal() {
        assertFalse(isForkApkDownload(ownAsset.replace(".apk", ".zip")))
        assertFalse(isForkApkDownload("$ownAsset?token=abc"))
        assertFalse(isForkApkDownload("$ownAsset#fragment"))
        assertFalse(isForkApkDownload(ownAsset.replace("releases/download/", "releases/")))
        assertFalse(
            isForkApkDownload(
                "https://github.com/conradlyn/ReadYou/releases/download/../../evil.apk"
            )
        )
    }

    @Test
    fun rejectsBlankAndMalformedUrls() {
        assertFalse(isForkApkDownload(""))
        assertFalse(isForkApkDownload("not a url"))
    }
}
