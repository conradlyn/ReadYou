package me.ash.reader.domain.service

import me.ash.reader.domain.model.general.toForkVersion
import me.ash.reader.infrastructure.net.LatestRelease
import me.ash.reader.infrastructure.net.NetworkDataSource

/**
 * GitHub's `/releases/latest` skips drafts and pre-releases. This fork publishes a pre-release on
 * every push, so that endpoint alone would never surface them once a stable release exists (and it
 * answers 404 while the repo has no stable release at all). Both requests are therefore made and
 * the newer tag wins, using [toForkVersion] so `v0.16.2-tablet.N` tags compare correctly.
 *
 * The releases list is not reliably chronological: GitHub pins the newest *stable* release at the
 * head even when a newer pre-release exists. Never take its first entry; scan the whole page and
 * pick the highest tag instead.
 *
 * Kept out of [AppService] so that an upstream change to `checkUpdate()` stays easy to merge:
 * the caller only swaps one call in place of the old `getReleaseLatest` line.
 */
internal class ForkUpdateLookup(
    val release: LatestRelease?,
    val rateLimited: Boolean = false,
)

/** Pure selection, so the "newer preview wins" rule can be unit tested without Retrofit. */
internal fun newerRelease(stable: LatestRelease?, newest: LatestRelease?): LatestRelease? =
    when {
        stable == null -> newest
        newest == null -> stable
        newest.tag_name.toForkVersion() > stable.tag_name.toForkVersion() -> newest
        else -> stable
    }

/**
 * Highest tag in a releases page. Uses the Version operator directly rather than maxByOrNull,
 * because [me.ash.reader.domain.model.general.Version] defines `compareTo` but does not implement
 * `Comparable`. Ties keep the earlier entry, so a stable release wins over an equally tagged
 * preview, matching [newerRelease].
 */
internal fun newestByVersion(releases: List<LatestRelease>?): LatestRelease? =
    releases.orEmpty().fold(null as LatestRelease?) { best, candidate ->
        when {
            best == null -> candidate
            candidate.tag_name.toForkVersion() > best.tag_name.toForkVersion() -> candidate
            else -> best
        }
    }

internal suspend fun fetchForkUpdate(
    dataSource: NetworkDataSource,
    latestUrl: String,
    listUrl: String,
): ForkUpdateLookup {
    val latest = dataSource.getReleaseLatest(latestUrl)
    val latestLimited = latest.code() == 403
    // 404 is expected while the repo has no stable release; anything else is a real failure.
    if (!latestLimited && latest.code() != 404 && latest.code() != 200) {
        return ForkUpdateLookup(release = null)
    }

    // The list includes pre-releases, so read it even when /releases/latest succeeded. Otherwise
    // the first stable release would pin "latest" forever and later preview builds stay invisible.
    // Its first entry is that pinned stable release, hence the version scan over the whole page.
    val list = dataSource.getReleases(listUrl)
    val listLimited = list.code() == 403
    val stable = latest.body()
    val newest = if (listLimited) null else newestByVersion(list.body())
    val chosen = newerRelease(stable, newest)
    return when {
        chosen != null -> ForkUpdateLookup(release = chosen)
        else -> ForkUpdateLookup(release = null, rateLimited = latestLimited || listLimited)
    }
}
