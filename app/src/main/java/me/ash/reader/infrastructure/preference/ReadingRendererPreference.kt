package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.DataStoreKey.Companion.readingRenderer
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.put

val LocalReadingRenderer =
    compositionLocalOf<ReadingRendererPreference> { ReadingRendererPreference.default }

sealed class ReadingRendererPreference(val value: Int) : Preference() {
    object WebView : ReadingRendererPreference(0)
    object NativeComponent : ReadingRendererPreference(1)

    override fun put(context: Context, scope: CoroutineScope) {
        scope.launch {
            context.dataStore.put(DataStoreKey.readingRenderer, value)
        }
    }

    @Stable
    fun toDesc(context: Context): String =
        when (this) {
            WebView -> context.getString(R.string.webview)
            NativeComponent -> context.getString(R.string.native_component)
        }

    companion object {

        // The native LazyColumn path is measurably smoother on the target tablet.
        // An explicit DataStore choice still wins; this only changes the fallback.
        //
        // Getters, not initialized vals: constructing a nested `object` triggers this class's
        // <clinit>, so if the JVM enters through one of those objects first, field initializers
        // here can capture its half-built INSTANCE as null (observed on a plain JVM as
        // `default == null` and `values == [WebView, null]`). Resolving the singletons on access
        // makes both values independent of class-initialization order.
        val default
            get() = NativeComponent
        val values
            get() = listOf(WebView, NativeComponent)

        fun fromPreferences(preferences: Preferences) =
            when (preferences[DataStoreKey.keys[readingRenderer]?.key as Preferences.Key<Int>]) {
                0 -> WebView
                1 -> NativeComponent
                else -> default
            }
    }
}
