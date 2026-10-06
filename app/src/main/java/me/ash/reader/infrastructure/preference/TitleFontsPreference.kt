package me.ash.reader.infrastructure.preference

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import me.ash.reader.R
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.ListExternalFonts
import me.ash.reader.ui.theme.GoogleSansFontFamily

/**
 * Font family override for the *title* text of a page, layered on top of the page-wide font.
 *
 * Every other entry mirrors [ListFontsPreference] one to one - including the stored [value] - so the
 * title row offers the same fonts as the row above it and as the reading page.
 *
 * [Follow] means "no override": the title keeps whatever the page's own font row says. It is the
 * default, which is what makes splitting one font setting into two a no-op for anyone who never
 * opens the title dialog.
 *
 * Because one of the two rows is the page-wide one, every (title, body) pair is reachable without a
 * third state: set the page row to the body font, then the title row to the title font. Doing it the
 * other way round works too, since the page row is the fallback rather than a hard constraint.
 */
sealed class TitleFontsPreference(val value: Int) {

    object Follow : TitleFontsPreference(-1)

    object GoogleSans : TitleFontsPreference(6)

    object System : TitleFontsPreference(0)

    object Serif : TitleFontsPreference(1)

    object SansSerif : TitleFontsPreference(2)

    object Monospace : TitleFontsPreference(3)

    object Cursive : TitleFontsPreference(4)

    object External : TitleFontsPreference(5)

    /** `null` means "no override", i.e. keep the page font. */
    fun asFontFamily(context: Context): FontFamily? =
        when (this) {
            Follow -> null
            GoogleSans -> GoogleSansFontFamily
            System -> FontFamily.Default
            Serif -> FontFamily.Serif
            SansSerif -> FontFamily.SansSerif
            Monospace -> FontFamily.Monospace
            Cursive -> FontFamily.Cursive
            External ->
                ExternalFonts.loadReadingTypography(context).displayLarge.fontFamily
                    ?: FontFamily.Default
        }

    /**
     * Same as [asFontFamily], except that [External] resolves to [externalSlot]'s imported font
     * rather than the reading page's.
     *
     * This enum is shared by the title rows of the flow and reading pages and by the flow page's
     * summary row, so [External] cannot mean one fixed font. Each caller passes its own slot, which
     * is what lets a row honour the file imported on that row.
     *
     * `null` therefore has two meanings, and every caller treats them the same way: [Follow], and
     * [External] on a row with nothing imported yet. Both fall back to the page's own font - which
     * for the reading page's title is the reading font, the very file that row used before it had a
     * slot of its own, so an existing `External` title keeps its appearance.
     */
    fun asFontFamily(context: Context, externalSlot: ListExternalFonts.Slot): FontFamily? =
        if (this == External) {
            ListExternalFonts.load(context, externalSlot)
        } else {
            asFontFamily(context)
        }

    fun toDesc(context: Context): String =
        when (this) {
            Follow -> context.getString(R.string.follow_page_font)
            GoogleSans -> context.getString(R.string.google_sans)
            System -> context.getString(R.string.system_default)
            Serif -> "Serif"
            SansSerif -> "Sans-Serif"
            Monospace -> "Monospace"
            Cursive -> "Cursive"
            External -> context.getString(R.string.import_font)
        }

    companion object {

        val default: TitleFontsPreference = Follow

        /** Mirrors [ListFontsPreference.values], with [Follow] in place of its `Default`. */
        val values =
            listOf(Follow, GoogleSans, System, Serif, SansSerif, Monospace, Cursive, External)

        fun fromValue(value: Int?): TitleFontsPreference =
            values.firstOrNull { it.value == value } ?: default
    }
}
