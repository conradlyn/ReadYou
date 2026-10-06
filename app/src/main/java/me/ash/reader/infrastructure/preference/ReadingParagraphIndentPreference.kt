package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingParagraphIndent
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalReadingParagraphIndent =
    compositionLocalOf<ReadingParagraphIndentPreference> { ReadingParagraphIndentPreference.default }

sealed class ReadingParagraphIndentPreference(val value: Boolean) : Preference() {
    object ON : ReadingParagraphIndentPreference(true)
    object OFF : ReadingParagraphIndentPreference(false)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(
                DataStoreKey.readingParagraphIndent,
                value
            )
        }
    }

    companion object {

        val default = ON
        val values = listOf(ON, OFF)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[readingParagraphIndent]?.key as Preferences.Key<Boolean>]) {
                true -> ON
                false -> OFF
                else -> default
            }
    }
}

operator fun ReadingParagraphIndentPreference.not(): ReadingParagraphIndentPreference =
    when (value) {
        true -> ReadingParagraphIndentPreference.OFF
        false -> ReadingParagraphIndentPreference.ON
    }
