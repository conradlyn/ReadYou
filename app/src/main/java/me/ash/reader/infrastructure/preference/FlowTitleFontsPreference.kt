package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowTitleFonts
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * The article title font of the flow (article list) page.
 *
 * Separate from [LocalFlowFonts], which stays the page-wide font and therefore the fallback: when
 * this is [TitleFontsPreference.Follow] the title keeps the list font.
 */
val LocalFlowTitleFonts = compositionLocalOf<TitleFontsPreference> { TitleFontsPreference.default }

object FlowTitleFontsPreference {

    fun put(context: Context, scope: CoroutineScope, value: TitleFontsPreference) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowTitleFonts, value.value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        TitleFontsPreference.fromValue(
            preferences[DataStoreKey.keys[flowTitleFonts]?.key as Preferences.Key<Int>]
        )
}
