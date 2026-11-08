package me.ash.reader.domain.model.general

/**
 * Release tags in this fork look like `v0.16.2-tablet.26`, while upstream tags are plain
 * `0.16.3`. [Version] keeps only the first three dot-separated integers, so a fork tag would
 * parse as `0.16.0` (`v0` and `2-tablet` are not integers) and could never compare greater than
 * the installed `versionName` (`0.16.2`) -- "check for updates" would then stay silent forever.
 *
 * Both the release tag and the installed APK are mapped into the same three-field space:
 * `major.minor.<point * ForkVersionPointScale + tablet serial>`, so
 * `v0.16.2-tablet.26` -> `0.16.20026`, `v0.16.2-tablet.27` -> `0.16.20027`.
 * Keeping the versionName point in the number matters: after an upstream versionName bump the
 * serial restarts at 1, and without it `v0.16.3-tablet.1` would compare *older* than tablet.29.
 * Tags that do not match the fork shape keep the upstream parser, so an upstream-style release is
 * unaffected.
 *
 * Kept as a pure function in a new file so upstream changes to [Version] never conflict.
 */
private val ForkTagPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)-tablet\.(\d+)$""")

/** Room for 9999 tablet builds per versionName before the point field would bleed into the next. */
private const val ForkVersionPointScale = 10_000L

private fun combinedPoint(point: String, serial: String): String {
    val base = (point.toLongOrNull() ?: 0L).coerceAtLeast(0L)
    val build = (serial.toLongOrNull() ?: 0L).coerceAtLeast(0L)
    return (base * ForkVersionPointScale + build)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toString()
}

fun String?.toForkVersion(): Version {
    val trimmed = this?.trim()
    if (trimmed == null) return Version()
    val match = ForkTagPattern.matchEntire(trimmed) ?: return trimmed.toVersion()
    val (major, minor, point, serial) = match.destructured
    return Version(listOf(major, minor, combinedPoint(point, serial)))
}

/**
 * Version of the *installed* APK in the same coordinate system as [toForkVersion], so
 * `v0.16.2-tablet.28` can be compared against the build itself rather than against a hard-coded
 * `versionName` that is `0.16.2` for every tablet build.
 *
 * [tabletBuild] is `BuildConfig.TABLET_BUILD`, injected by the release workflow before assemble.
 * Local / non-release builds pass 0 and keep the upstream behaviour (point field from
 * `versionName`), so they still see published builds as newer instead of pretending to be one.
 *
 * Pure so it can be unit tested on the JVM; the Android adapter lives in ForkUpdateSupport.kt.
 */
fun installedForkVersion(versionName: String?, tabletBuild: Int): Version {
    val parts = versionName.orEmpty().split(".")
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val point = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val mappedPoint =
        if (tabletBuild > 0) combinedPoint(point.toString(), tabletBuild.toString())
        else point.toString()
    return Version(listOf(major.toString(), minor.toString(), mappedPoint))
}

/** Label shown on the About page, so the installed tablet build is visible to the user. */
fun installedForkVersionLabel(versionName: String?, tabletBuild: Int): String =
    if (tabletBuild > 0) "${versionName.orEmpty()}-tablet.$tabletBuild"
    else versionName.orEmpty()
