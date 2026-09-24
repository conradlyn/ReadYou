package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.uiTextScale
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalUiTextScale = compositionLocalOf { UiTextScalePreference.default }

/**
 * Size of the text the app draws for itself, as a percentage of the stock appearance.
 *
 * "The app's own text" means the settings pages and every dialog - the chrome around the content,
 * as opposed to the content itself. The three list/reading font sizes
 * ([FeedsTextFontSizePreference], [FlowTextFontSizePreference], [ReadingTextFontSizePreference])
 * are a separate axis on purpose: they size text the user reads, this sizes the interface that
 * lets them adjust it. Folding the two together would double-scale the list pages, because their
 * styles are built *relative* to the current typography (see `ui/component/ListFonts.kt`).
 *
 * 100 is the default and is an exact identity - not "close to" upstream but the same dp and the
 * same `sp` values, with the typography returned as the very same instance. A phone that never
 * touches this setting is therefore pixel-identical to upstream, which is the property that keeps
 * this whole layer acceptable as a fork. `UiTextScaleTest` is the alarm if that ever stops being
 * true.
 *
 * The range starts at 100 rather than below it: the complaint this answers is that the interface
 * is too small on a large screen, and a setting whose most interesting value is "smaller than
 * stock" would be a different feature.
 */
object UiTextScalePreference {

    const val default = 100
    const val min = 100
    const val max = 150

    fun Int.coerceToRange(): Int = coerceIn(min..max)

    fun put(context: Context, scope: CoroutineScope, value: Int) {
        scope.launch {
            context.dataStore.put(DataStoreKey.uiTextScale, value)
        }
    }

    fun fromPreferences(preferences: Preferences) =
        preferences[DataStoreKey.keys[uiTextScale]?.key as Preferences.Key<Int>] ?: default
}
