package me.ash.reader.ui.page.nav3

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import me.ash.reader.ui.theme.ProvideUiTextScale

/**
 * A [NavEntry] whose content renders at the app's own text size.
 *
 * Every settings route uses this instead of `NavEntry(...)`, which is the whole mechanism: it is
 * one token per route and no re-indentation, so the 19 settings branches stay a two-line diff
 * apiece against upstream.
 *
 * ## Why the scope is per-route rather than around `NavDisplay`
 *
 * `NavEntry`'s content lambda is *captured* by the entry provider and invoked later, inside
 * `NavDisplay`'s own composition. Composition locals are resolved where the composable runs, not
 * where it is written, so wrapping the body of `entryProvider` - or `NavDisplay` itself - would
 * put the provider outside the subtree it is meant to cover and do nothing at all.
 *
 * The scope also cannot be the app root, and cannot be `RYScaffold`: the list pages read their
 * base font size out of the current typography (`ui/component/ListFonts.kt`), so anything wider
 * than the settings routes would size their text twice. `FeedsPage` and `FlowPage` are exactly
 * the pages that must not be covered, and they are also two of the three non-settings callers of
 * `RYScaffold`.
 *
 * Dialogs opened from a settings page inherit this scope, and [me.ash.reader.ui.component.base.RYDialog]
 * applies the same scope to every dialog regardless of origin, so the two agree rather than
 * compounding - see `ProvideUiTextScale`.
 */
fun settingsNavEntry(key: NavKey, content: @Composable () -> Unit): NavEntry<NavKey> =
    NavEntry(key) { ProvideUiTextScale { content() } }
