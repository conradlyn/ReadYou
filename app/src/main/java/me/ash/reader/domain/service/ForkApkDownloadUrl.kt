package me.ash.reader.domain.service

import java.net.URI
import java.net.URISyntaxException

private const val ForkDownloadPathPrefix = "/conradlyn/ReadYou/releases/download/"

/**
 * Only URLs published by this fork's releases may be downloaded and handed to the package
 * installer. The download URL comes from release metadata over the network, so it is treated as
 * untrusted input; a plain "starts with https" check would be too weak for something we install.
 *
 * Deliberately free of Android imports, so plain JVM unit tests can exercise it without loading
 * BuildConfig or the mockable android.jar.
 */
fun isForkApkDownload(url: String): Boolean =
    try {
        val uri = URI(url)
        val path = uri.rawPath.orEmpty()
        uri.scheme == "https" &&
            uri.host == "github.com" &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            path.startsWith(ForkDownloadPathPrefix) &&
            path.endsWith(".apk", ignoreCase = true) &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            !path.contains("..")
    } catch (e: URISyntaxException) {
        false
    }
