package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingTitleFonts
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * The article title font of the reading page.
 *
 * Separate from [LocalReadingFonts], which stays the body font (and the fallback): when this is
 * [TitleFontsPreference.Follow] the headline keeps the reading font. The date, author and feed name
 * above and below the title are body text, not the title, and keep following [LocalReadingFonts].
 */
val LocalReadingTitleFonts =
    compositionLocalOf<TitleFontsPreference> { TitleFontsPreference.default }

object ReadingTitleFontsPreference {

    fun put(context: Context, scope: CoroutineScope, value: TitleFontsPreference) {
        scope.launch {
            context.dataStore.put(DataStoreKey.readingTitleFonts, value.value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        TitleFontsPreference.fromValue(
            preferences[DataStoreKey.keys[readingTitleFonts]?.key as Preferences.Key<Int>]
        )
}
