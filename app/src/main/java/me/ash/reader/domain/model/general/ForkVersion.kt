package me.ash.reader.domain.model.general

/**
 * Release tags in this fork look like `v0.16.2-tablet.26`, while upstream tags are plain
 * `0.16.3`. [Version] keeps only the first three dot-separated integers, so a fork tag would
 * parse as `0.16.0` (`v0` and `2-tablet` are not integers) and could never compare greater than
 * the installed `versionName` (`0.16.2`) -- "check for updates" would then stay silent forever.
 *
 * Map the tablet build serial into the point field so successive builds remain ordered:
 * `v0.16.2-tablet.26` -> `0.16.26`, `v0.16.2-tablet.27` -> `0.16.27`. Tags that do not match
 * the fork shape keep the upstream parser, so an upstream-style release is unaffected.
 *
 * Kept as a pure function in a new file so upstream changes to [Version] never conflict.
 */
private val ForkTagPattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)-tablet\.(\d+)$""")

fun String?.toForkVersion(): Version {
    val trimmed = this?.trim()
    if (trimmed == null) return Version()
    val match = ForkTagPattern.matchEntire(trimmed) ?: return trimmed.toVersion()
    val (major, minor, _, serial) = match.destructured
    return Version(listOf(major, minor, serial))
}
