package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.markAsReadButtonPosition
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalMarkAsReadButtonPosition =
    compositionLocalOf<MarkAsReadButtonPositionPreference> { MarkAsReadButtonPositionPreference.default }

/**
 * Where the "mark as read" (DoneAll) button of the flow page lives.
 *
 * - [Top]: in the top app bar next to search, which is the upstream layout.
 * - [Bottom]: as the leading item of the bottom filter bar, which is far easier to reach with a
 *   thumb on a tablet held in both hands.
 *
 * This backs the `mark_as_read_button_position` row that used to be a disabled placeholder in the
 * "Flow page" settings screen.
 */
sealed class MarkAsReadButtonPositionPreference(val value: Int) : Preference() {
    data object Top : MarkAsReadButtonPositionPreference(0)
    data object Bottom : MarkAsReadButtonPositionPreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                markAsReadButtonPosition,
                value
            )
        }
    }

    val description: String
        @Composable get() =
            when (this) {
                Top -> stringResource(id = R.string.top)
                Bottom -> stringResource(id = R.string.bottom)
            }

    companion object {

        val default = Bottom
        val values = listOf(Top, Bottom)

        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[markAsReadButtonPosition]?.key as Preferences.Key<Int>
                ]
            ) {
                0 -> Top
                1 -> Bottom
                else -> default
            }
    }
}
