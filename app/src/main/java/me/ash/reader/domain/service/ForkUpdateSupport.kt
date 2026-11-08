package me.ash.reader.domain.service

import android.content.Context
import me.ash.reader.BuildConfig
import me.ash.reader.domain.model.general.Version
import me.ash.reader.domain.model.general.installedForkVersion
import me.ash.reader.domain.model.general.installedForkVersionLabel

/**
 * Android adapters for the fork's update arithmetic. Keeping them here means upstream changes to
 * ContextExt / Version / AppService never have to be untangled from the tablet build serial.
 * The pure arithmetic and the download-URL whitelist live in ForkVersion.kt and
 * ForkApkDownloadUrl.kt, so they stay directly testable on a plain JVM.
 */

/** The installed APK's version, comparable with `String.toForkVersion()` results. */
fun Context.getInstalledForkVersion(): Version =
    installedForkVersion(
        packageManager.getPackageInfo(packageName, 0).versionName,
        BuildConfig.TABLET_BUILD,
    )

/** Human-readable build label for the About page, e.g. `0.16.2-tablet.28`. */
fun Context.getInstalledForkVersionLabel(): String =
    installedForkVersionLabel(
        packageManager.getPackageInfo(packageName, 0).versionName,
        BuildConfig.TABLET_BUILD,
    )
