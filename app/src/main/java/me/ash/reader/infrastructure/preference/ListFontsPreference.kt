package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import me.ash.reader.R
import me.ash.reader.ui.ext.ListExternalFonts
import me.ash.reader.ui.theme.GoogleSansFontFamily

/**
 * Font family options shared by the feeds page and the flow (article list) page.
 *
 * [Default] keeps the app theme font, i.e. the stock appearance, and is the default of both pages.
 * Every other entry mirrors [ReadingFontsPreference] one to one - including the stored [value] - so
 * both list pages offer exactly the same fonts as the reading page.
 */
sealed class ListFontsPreference(val value: Int) {

    object Default : ListFontsPreference(-1)

    object GoogleSans : ListFontsPreference(6)

    object System : ListFontsPreference(0)

    object Serif : ListFontsPreference(1)

    object SansSerif : ListFontsPreference(2)

    object Monospace : ListFontsPreference(3)

    object Cursive : ListFontsPreference(4)

    object External : ListFontsPreference(5)

    /**
     * `null` means "no override", i.e. keep the app theme font.
     *
     * [externalSlot] says which imported font [External] resolves to. Both list pages share this
     * enum but not their import, so the slot has to come from the caller rather than from the
     * preference value.
     */
    fun asFontFamily(context: Context, externalSlot: ListExternalFonts.Slot): FontFamily? =
        when (this) {
            Default -> null
            GoogleSans -> GoogleSansFontFamily
            System -> FontFamily.Default
            Serif -> FontFamily.Serif
            SansSerif -> FontFamily.SansSerif
            Monospace -> FontFamily.Monospace
            Cursive -> FontFamily.Cursive
            External -> ListExternalFonts.load(context, externalSlot) ?: FontFamily.Default
        }

    fun toDesc(context: Context): String =
        when (this) {
            Default -> context.getString(R.string.use_app_theme)
            GoogleSans -> context.getString(R.string.google_sans)
            System -> context.getString(R.string.system_default)
            Serif -> "Serif"
            SansSerif -> "Sans-Serif"
            Monospace -> "Monospace"
            Cursive -> "Cursive"
            External -> context.getString(R.string.external_fonts)
        }

    companion object {

        val default: ListFontsPreference = Default

        /** Mirrors [ReadingFontsPreference.values], with [Default] prepended. */
        val values =
            listOf(Default, GoogleSans, System, Serif, SansSerif, Monospace, Cursive, External)

        fun fromValue(value: Int?): ListFontsPreference =
            values.firstOrNull { it.value == value } ?: default
    }
}
