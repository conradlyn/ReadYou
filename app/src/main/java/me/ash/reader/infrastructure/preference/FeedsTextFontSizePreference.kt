package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.feedsTextFontSize
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFeedsTextFontSize = compositionLocalOf { FeedsTextFontSizePreference.default }

/**
 * Size, in `sp`, of the primary text of the feeds page (the group name).
 *
 * Every other text of the page is scaled proportionally, so [baseline] - the size that text has in
 * the stock appearance - is an identity transform and leaves the page untouched.
 *
 * [baseline] is not a round number picked by hand. The text it sizes is the group name, drawn in
 * `MaterialTheme.typography.titleMedium` (see `ui/page/home/feeds/GroupItem.kt`), and the app
 * overrides only `bodySmallEmphasized` of Material 3's typography (see `SystemTypography` in
 * `ui/theme/Type.kt`), so it is Material 3's default of 16sp. Nothing in this file enforces that,
 * which is why `ListFontBaselineTest` asserts it: if a Compose BOM bump moves that default, a test
 * fails instead of every list quietly rendering at the wrong size.
 */
object FeedsTextFontSizePreference {

    const val baseline = 16
    const val min = 10
    const val max = 32
    const val default = baseline

    fun Int.coerceToRange(): Int = coerceIn(min..max)

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.feedsTextFontSize, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[feedsTextFontSize]?.key as Preferences.Key<Int>] ?: default
}
