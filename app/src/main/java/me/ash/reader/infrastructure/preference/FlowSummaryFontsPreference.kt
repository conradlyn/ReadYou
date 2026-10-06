package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.flowSummaryFonts
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

/**
 * The article summary font of the flow (article list) page.
 *
 * The third font row of that page, after [LocalFlowFonts] and [LocalFlowTitleFonts]. The summary
 * used to be drawn with the list font unconditionally, which left it as the one piece of the
 * article card with no setting of its own.
 *
 * [TitleFontsPreference] is reused rather than a new enum, so this row offers exactly the same fonts
 * as the title row above it. In particular [TitleFontsPreference.Follow] is the default, meaning
 * "keep the list font" - the stock appearance - so a reader who never opens this dialog sees no
 * change at all.
 */
val LocalFlowSummaryFonts = compositionLocalOf<TitleFontsPreference> { TitleFontsPreference.default }

object FlowSummaryFontsPreference {

    fun put(context: Context, scope: CoroutineScope, value: TitleFontsPreference) {
        scope.launch {
            context.dataStore.put(DataStoreKey.flowSummaryFonts, value.value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        TitleFontsPreference.fromValue(
            preferences[DataStoreKey.keys[flowSummaryFonts]?.key as Preferences.Key<Int>]
        )
}
