package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowFonts
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalFlowFonts = compositionLocalOf { ListFontsPreference.default }

object FlowFontsPreference {

    fun put(context: Context, scope: CoroutineScope, value: ListFontsPreference) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowFonts, value.value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        ListFontsPreference.fromValue(
            preferences[DataStoreKey.keys[flowFonts]?.key as Preferences.Key<Int>]
        )
}
