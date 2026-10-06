package me.ash.reader.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import me.ash.reader.infrastructure.preference.FeedsTextFontSizePreference
import me.ash.reader.infrastructure.preference.FlowTextFontSizePreference
import me.ash.reader.infrastructure.preference.ListFontsPreference
import me.ash.reader.infrastructure.preference.LocalFeedsFonts
import me.ash.reader.infrastructure.preference.LocalFeedsTextFontSize
import me.ash.reader.infrastructure.preference.LocalFlowFonts
import me.ash.reader.infrastructure.preference.LocalFlowSummaryFonts
import me.ash.reader.infrastructure.preference.LocalFlowTextFontSize
import me.ash.reader.infrastructure.preference.LocalFlowTitleFonts
import me.ash.reader.infrastructure.preference.TitleFontsPreference
import me.ash.reader.ui.ext.ListExternalFonts

/**
 * Applies the feeds page font family and font size to one of its list text styles.
 *
 * The size is an absolute `sp` value for the primary text of the page (the group name); every other
 * text is scaled by the same factor, so the visual hierarchy of the list is preserved and the
 * baseline size - the stock appearance - is an identity transform.
 */
@Composable
fun TextStyle.withFeedsListStyle(): TextStyle =
    withListStyle(
        fontFamily = listFontFamily(LocalFeedsFonts.current, ListExternalFonts.Slot.Feeds),
        sizeSp = LocalFeedsTextFontSize.current,
        baselineSp = FeedsTextFontSizePreference.baseline,
    )

/** Applies the flow page font family and font size to one of its list text styles. */
@Composable
fun TextStyle.withFlowListStyle(): TextStyle =
    withListStyle(
        fontFamily = listFontFamily(LocalFlowFonts.current, ListExternalFonts.Slot.Flow),
        sizeSp = LocalFlowTextFontSize.current,
        baselineSp = FlowTextFontSizePreference.baseline,
    )

/**
 * Same as [withFlowListStyle], but for the article title.
 *
 * The title has a font row of its own; when that row says nothing - [TitleFontsPreference.Follow],
 * or "Import font" with nothing imported yet - it falls back to the list font, so the size scaling -
 * and with it the baseline the font-size guard watches - stays identical to every other row.
 */
@Composable
fun TextStyle.withFlowTitleStyle(): TextStyle =
    withListStyle(
        fontFamily =
            overridingFontFamily(LocalFlowTitleFonts.current, ListExternalFonts.Slot.FlowTitle)
                ?: listFontFamily(LocalFlowFonts.current, ListExternalFonts.Slot.Flow),
        sizeSp = LocalFlowTextFontSize.current,
        baselineSp = FlowTextFontSizePreference.baseline,
    )

/**
 * Same as [withFlowTitleStyle], but for the article summary.
 *
 * The summary used to be drawn with the list font unconditionally, which left it as the one piece
 * of the article card with no setting of its own.
 */
@Composable
fun TextStyle.withFlowSummaryStyle(): TextStyle =
    withListStyle(
        fontFamily =
            overridingFontFamily(LocalFlowSummaryFonts.current, ListExternalFonts.Slot.FlowSummary)
                ?: listFontFamily(LocalFlowFonts.current, ListExternalFonts.Slot.Flow),
        sizeSp = LocalFlowTextFontSize.current,
        baselineSp = FlowTextFontSizePreference.baseline,
    )

/**
 * Resolves a row that either overrides the page font or follows it - the title and summary rows of
 * the flow page, and the title row of the reading page.
 *
 * `null` means "no override", for both ways a row can say that: [TitleFontsPreference.Follow], and
 * `External` on a row with nothing imported yet. Callers fall back to the page font in both cases,
 * which is what keeps a half-configured row from dropping to a system font.
 *
 * The [ListExternalFonts.generation] read is the same guard [listFontFamily] carries, for the same
 * reason: re-importing into a row that already says `External` changes no preference value, so
 * without this key the cached `FontFamily` would outlive the file it was built from.
 */
@Composable
fun overridingFontFamily(
    preference: TitleFontsPreference,
    slot: ListExternalFonts.Slot,
): FontFamily? {
    val context = LocalContext.current
    val generation = ListExternalFonts.generation(slot)
    return remember(preference, generation, context) { preference.asFontFamily(context, slot) }
}

/**
 * Resolves a list page's font family, re-reading it when the page's imported file is replaced.
 *
 * The [ListExternalFonts.generation] read is the whole point of this wrapper. Picking `External`
 * stores the same preference value whether or not a font has been imported, so importing a *second*
 * file into an already-`External` slot changes no preference at all - without this key the cached
 * `FontFamily` would survive and the new file would only appear after an app restart.
 */
@Composable
private fun listFontFamily(
    preference: ListFontsPreference,
    slot: ListExternalFonts.Slot,
): FontFamily? {
    val context = LocalContext.current
    val generation = ListExternalFonts.generation(slot)
    return remember(preference, generation, context) {
        preference.asFontFamily(context, slot)
    }
}

@Composable
private fun TextStyle.withListStyle(
    fontFamily: FontFamily?,
    sizeSp: Int,
    baselineSp: Int,
): TextStyle {
    val scale = if (sizeSp > 0 && baselineSp > 0) sizeSp.toFloat() / baselineSp else 1f
    if (fontFamily == null && scale == 1f) return this
    return copy(
        fontFamily = fontFamily ?: this.fontFamily,
        fontSize = fontSize.scaledBy(scale),
        lineHeight = lineHeight.scaledBy(scale),
    )
}

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (this == TextUnit.Unspecified) this else this * scale
