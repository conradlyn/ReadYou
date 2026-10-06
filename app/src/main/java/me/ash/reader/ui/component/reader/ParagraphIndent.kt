package me.ash.reader.ui.component.reader

/**
 * Chinese body text opens with a two-character indent; Latin text does not.
 *
 * Neither an article nor a feed carries a language, so the verdict has to be read off the text
 * itself. Kept free of Android and Compose types on purpose: the WebView stylesheet and the native
 * reader both consume it, and the rules stay unit-testable.
 */

/**
 * Two ideographic spaces, i.e. exactly two full-width characters. Because the width comes from the
 * characters rather than from a measurement, the indent follows the font size and the reading font
 * on its own.
 */
const val PARAGRAPH_INDENT_TEXT = "\u3000\u3000"

/**
 * The same indent expressed in CSS. One full-width character is one `em`, so two of them are `2em`.
 * `ch` would be the wrong unit here - it is the width of "0", roughly half a full-width character.
 */
const val PARAGRAPH_INDENT_CSS = "2em"

private fun Char.isHan(): Boolean =
    this in '\u4E00'..'\u9FFF' || // CJK Unified Ideographs
        this in '\u3400'..'\u4DBF' || // Extension A
        this in '\uF900'..'\uFAFF' // Compatibility Ideographs

/**
 * Kana. Japanese is written with Han characters as well, so Han alone cannot tell the two apart.
 */
private fun Char.isKana(): Boolean = this in '\u3040'..'\u309F' || this in '\u30A0'..'\u30FF'

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * True when [text] reads as Chinese rather than as English or Japanese.
 *
 * The share of Han characters among Han + Latin letters separates the cases by a wide margin: a
 * Chinese article stays around 0.8 even after its markup and any English terms are counted, while
 * an English article sits at 0. Japanese is rejected through [kanaThreshold], because its own
 * convention is a one-character indent rather than two.
 *
 * @param text the article body. Markup is fine: tag names only add Latin letters, and they are far
 *   too few to move a Chinese article below the threshold.
 * @param hanThreshold the smallest Han share that still counts as Chinese.
 * @param kanaThreshold the kana share, among Han + kana, above which the text is treated as
 *   Japanese. Chinese text that quotes a Japanese phrase stays far below it.
 */
fun isChineseDominant(
    text: String,
    hanThreshold: Float = 0.2f,
    kanaThreshold: Float = 0.05f,
): Boolean {
    var han = 0
    var latin = 0
    var kana = 0
    for (c in text) {
        when {
            c.isHan() -> han++
            c.isKana() -> kana++
            c.isLatinLetter() -> latin++
        }
    }
    val cjk = han + kana
    if (cjk == 0) return false
    if (kana.toFloat() / cjk >= kanaThreshold) return false
    return han.toFloat() / (han + latin) >= hanThreshold
}

/**
 * Whether body paragraphs get an indent: the preference is on and the article is Chinese. An
 * English article is excluded by the verdict, not by the preference.
 */
fun shouldIndentParagraphs(enabled: Boolean, isChinese: Boolean): Boolean = enabled && isChinese

/** The prefix to put in front of a body paragraph, or `""` when no indent is wanted. */
fun paragraphIndentText(enabled: Boolean, isChinese: Boolean): String =
    if (shouldIndentParagraphs(enabled, isChinese)) PARAGRAPH_INDENT_TEXT else ""

/** The `text-indent` value for the WebView stylesheet, or `""` to leave the rule out entirely. */
fun paragraphIndentCss(enabled: Boolean, isChinese: Boolean): String =
    if (shouldIndentParagraphs(enabled, isChinese)) PARAGRAPH_INDENT_CSS else ""
