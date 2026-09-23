package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.markAllAsReadWithoutConfirm
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalMarkAllAsReadWithoutConfirm =
    compositionLocalOf<MarkAllAsReadWithoutConfirmPreference> { MarkAllAsReadWithoutConfirmPreference.default }

/**
 * When [ON], tapping the "mark as read" button marks the whole list read straight away instead of
 * unfolding the 7 / 3 / 1 day + all bar.
 *
 * OFF by default, so the two-step flow people are used to stays the default and no destructive
 * action happens from a single stray tap unless it was asked for.
 */
sealed class MarkAllAsReadWithoutConfirmPreference(val value: Boolean) : Preference() {
    data object ON : MarkAllAsReadWithoutConfirmPreference(true)
    data object OFF : MarkAllAsReadWithoutConfirmPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                markAllAsReadWithoutConfirm,
                value
            )
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(
            markAllAsReadWithoutConfirm,
            !value
        )
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[markAllAsReadWithoutConfirm]?.key as Preferences.Key<Boolean>
                ]
            ) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun MarkAllAsReadWithoutConfirmPreference.not(): MarkAllAsReadWithoutConfirmPreference =
    when (value) {
        true -> MarkAllAsReadWithoutConfirmPreference.OFF
        false -> MarkAllAsReadWithoutConfirmPreference.ON
    }
