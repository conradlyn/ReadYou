package me.ash.reader.ui.page.home.flow

/**
 * True when the flow page is showing articles from a single feed - which is exactly when its top
 * bar prints that feed's name.
 *
 * Mirrors the title's own precedence in `FlowPage`, where `group` beats `feed`: a group-scoped page
 * titles itself with the folder name, so even if a feed happens to be selected alongside a group,
 * the name above each article is still the only thing saying where that article came from. Keeping
 * the two precedence orders identical is the point - they must never disagree about what the top
 * bar is currently naming.
 */
fun isSingleFeedFlow(groupScoped: Boolean, feedScoped: Boolean): Boolean = feedScoped && !groupScoped

/**
 * Whether an article row should print the name of the feed it belongs to.
 *
 * Dropped in a single-feed flow: the top bar already names that feed, so repeating it above every
 * one of its articles is pure noise - and on a tablet it is noise that costs a full text line per
 * row, which is a large share of a narrow list.
 *
 * Kept in every multi-source view ("All", a folder, starred, a search result). There the top bar
 * names the *collection* rather than the feed, so the row is the only place an article's origin is
 * stated at all.
 *
 * [preferenceEnabled] is the user's existing "feed names" switch. It keeps governing the
 * multi-source views; it can no longer bring the name back in a single-feed flow, because there it
 * would only duplicate the title.
 */
fun shouldShowArticleFeedName(preferenceEnabled: Boolean, singleFeedFlow: Boolean): Boolean =
    preferenceEnabled && !singleFeedFlow

/**
 * Whether an article row should draw the icon of the feed it belongs to.
 *
 * Same rule as [shouldShowArticleFeedName], for the same reason: in a single-feed flow every row
 * would draw the identical icon, so it says nothing about the article it sits beside.
 *
 * It is also the more expensive of the two to get half-right. The icon drags three other
 * measurements along with it - two reserves in the row's top line, and the sticky date header's own
 * indent - so dropping the icon while leaving any of them in place opens a 30dp hole in front of
 * every title. Keeping them in step is the whole job; that is why this is a function rather than
 * three inline copies of the same expression.
 *
 * Kept in every multi-source view: there the icon is the fastest way to tell whose article a row
 * is, and it is the only mark of origin that survives when the "feed names" switch is off.
 *
 * [preferenceEnabled] is the user's existing "feed icons" switch. As with the name, it keeps
 * governing the multi-source views and can no longer bring the icon back in a single-feed flow,
 * where it would only repeat the top bar.
 */
fun shouldShowArticleFeedIcon(preferenceEnabled: Boolean, singleFeedFlow: Boolean): Boolean =
    preferenceEnabled && !singleFeedFlow
