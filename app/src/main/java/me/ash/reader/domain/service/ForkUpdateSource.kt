package me.ash.reader.domain.service

import me.ash.reader.infrastructure.net.LatestRelease
import me.ash.reader.infrastructure.net.NetworkDataSource

/**
 * GitHub's `/releases/latest` skips drafts and pre-releases. This fork has published only
 * pre-releases so far, so that endpoint answers 404 even though the release feed is full, which
 * would make "check for updates" look broken. When it does, fall back to the newest entry of the
 * releases list (which does include pre-releases). A stable release, once published, still wins
 * on the primary request.
 *
 * Kept out of [AppService] so that an upstream change to `checkUpdate()` stays easy to merge:
 * the caller only swaps one call in place of the old `getReleaseLatest` line.
 */
internal class ForkUpdateLookup(
    val release: LatestRelease?,
    val rateLimited: Boolean = false,
)

internal suspend fun fetchForkUpdate(
    dataSource: NetworkDataSource,
    latestUrl: String,
    listUrl: String,
): ForkUpdateLookup {
    val latest = dataSource.getReleaseLatest(latestUrl)
    if (latest.code() == 403) return ForkUpdateLookup(release = null, rateLimited = true)
    latest.body()?.let { return ForkUpdateLookup(release = it) }
    // 404 here means "no stable release yet". Any other status is a real failure.
    if (latest.code() != 404) return ForkUpdateLookup(release = null)

    val list = dataSource.getReleases(listUrl)
    if (list.code() == 403) return ForkUpdateLookup(release = null, rateLimited = true)
    return ForkUpdateLookup(release = list.body()?.firstOrNull())
}
