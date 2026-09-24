package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingAutoFullContent
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalReadingAutoFullContent =
    compositionLocalOf<ReadingAutoFullContentPreference> { ReadingAutoFullContentPreference.default }

/**
 * When [ON], opening an article goes straight to the full content instead of the RSS description:
 * the fetch the toolbar's parse-full-content button triggers happens on open instead.
 *
 * OFF by default, so the network request stays something the reader asks for rather than something
 * every article triggers. The button itself keeps working either way - it is a toggle, so with this
 * on, the first tap is what turns the full content back into the description.
 *
 * Failure falls back to the description rather than an error message; [ON] is about saving a tap,
 * not about trading the text that is already sitting in the database for a failure notice.
 */
sealed class ReadingAutoFullContentPreference(val value: Boolean) : Preference() {
    data object ON : ReadingAutoFullContentPreference(true)
    data object OFF : ReadingAutoFullContentPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                readingAutoFullContent,
                value
            )
        }
    }

    fun toggle(context: Context, scope: CoroutineScope) = scope.launch {
        context.dataStore.put(
            readingAutoFullContent,
            !value
        )
    }

    companion object {

        val default = OFF
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (
                preferences[
                    DataStoreKey.keys[readingAutoFullContent]?.key as Preferences.Key<Boolean>
                ]
            ) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingAutoFullContentPreference.not(): ReadingAutoFullContentPreference =
    when (value) {
        true -> ReadingAutoFullContentPreference.OFF
        false -> ReadingAutoFullContentPreference.ON
    }
