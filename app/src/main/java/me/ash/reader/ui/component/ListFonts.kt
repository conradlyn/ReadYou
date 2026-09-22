package me.ash.reader.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.isSp
import me.ash.reader.infrastructure.preference.FeedsTextFontSizePreference
import me.ash.reader.infrastructure.preference.FlowTextFontSizePreference
import me.ash.reader.infrastructure.preference.LocalFeedsFonts
import me.ash.reader.infrastructure.preference.LocalFeedsTextFontSize
import me.ash.reader.infrastructure.preference.LocalFlowFonts
import me.ash.reader.infrastructure.preference.LocalFlowTextFontSize

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
        fontFamily = LocalFeedsFonts.current.asFontFamily(LocalContext.current),
        sizeSp = LocalFeedsTextFontSize.current,
        baselineSp = FeedsTextFontSizePreference.baseline,
    )

/** Applies the flow page font family and font size to one of its list text styles. */
@Composable
fun TextStyle.withFlowListStyle(): TextStyle =
    withListStyle(
        fontFamily = LocalFlowFonts.current.asFontFamily(LocalContext.current),
        sizeSp = LocalFlowTextFontSize.current,
        baselineSp = FlowTextFontSizePreference.baseline,
    )

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
        fontSize = if (fontSize.isSp) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight,
    )
}
