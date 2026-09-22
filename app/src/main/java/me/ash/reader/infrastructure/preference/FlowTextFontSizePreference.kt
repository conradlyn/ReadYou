package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowTextFontSize
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFlowTextFontSize = compositionLocalOf { FlowTextFontSizePreference.default }

/**
 * Size, in `sp`, of the primary text of the flow page (the article title).
 *
 * Every other text of the page is scaled proportionally, so [baseline] - the size that text has in
 * the stock appearance - is an identity transform and leaves the page untouched.
 */
object FlowTextFontSizePreference {

    const val baseline = 16
    const val min = 10
    const val max = 32
    const val default = baseline

    fun Int.coerceToRange(): Int = coerceIn(min..max)

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowTextFontSize, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[flowTextFontSize]?.key as Preferences.Key<Int>] ?: default
}
