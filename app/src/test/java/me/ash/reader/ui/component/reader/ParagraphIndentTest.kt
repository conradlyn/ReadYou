package me.ash.reader.ui.component.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphIndentTest {

    // --- the language verdict -------------------------------------------------------------------

    @Test
    fun `a Chinese article is Chinese`() {
        assertTrue(isChineseDominant("<p>这是一段中文正文，用来检验段首缩进的判定。</p>"))
    }

    @Test
    fun `an English article is not Chinese`() {
        assertFalse(
            isChineseDominant(
                "<p>This is an English article body, and it is deliberately long enough to look " +
                    "like real prose rather than a label.</p>"
            )
        )
    }

    @Test
    fun `empty text is not Chinese`() {
        assertFalse(isChineseDominant(""))
    }

    @Test
    fun `text with no letters at all is not Chinese`() {
        // Digits and punctuation only: no Han to count, so no indent.
        assertFalse(isChineseDominant("<p>2026 - 10 - 06 ... 1,234.5%</p>"))
    }

    @Test
    fun `a Japanese article is not Chinese`() {
        // Japanese also uses Han, so it is the kana that gives it away.
        assertFalse(
            isChineseDominant(
                "これは日本語の記事です。漢字とひらがなとカタカナが混ざっています。" +
                    "日本語の組版では字下げは一文字なので、二文字の字下げは当てはめない。"
            )
        )
    }

    @Test
    fun `kana alone is not Chinese`() {
        assertFalse(isChineseDominant("ひらがなだけのぶんしょうです。"))
    }

    @Test
    fun `a Chinese article quoting a Japanese phrase is still Chinese`() {
        // Two kana against a long Chinese body: the kana guard must not fire on a quotation.
        val body = "这是一段很长的中文正文，讨论排版与阅读体验。".repeat(10) + "引用「です」一词。"
        assertTrue(isChineseDominant(body))
    }

    @Test
    fun `an English article with one Chinese word is not Chinese`() {
        assertFalse(
            isChineseDominant(
                "<p>An English article that happens to mention 中文 once in passing, and then " +
                    "carries on in English for the rest of the paragraph.</p>"
            )
        )
    }

    @Test
    fun `the markup around a Chinese article does not change the verdict`() {
        val plain = "这是一段中文正文，用来检验段首缩进的判定。"
        assertTrue(isChineseDominant(plain))
        assertTrue(isChineseDominant("<article><p><strong>$plain</strong></p></article>"))
    }

    @Test
    fun `the Han share boundary is inclusive`() {
        // 20 Han out of 100 letters is exactly the threshold, so it counts as Chinese.
        assertTrue(isChineseDominant("汉".repeat(20) + "a".repeat(80)))
        // One Han short, and it does not.
        assertFalse(isChineseDominant("汉".repeat(19) + "a".repeat(81)))
    }

    // --- the decision ---------------------------------------------------------------------------

    @Test
    fun `paragraphs are indented only when the setting is on and the text is Chinese`() {
        assertTrue(shouldIndentParagraphs(enabled = true, isChinese = true))
        assertFalse(shouldIndentParagraphs(enabled = false, isChinese = true))
        assertFalse(shouldIndentParagraphs(enabled = true, isChinese = false))
        assertFalse(shouldIndentParagraphs(enabled = false, isChinese = false))
    }

    @Test
    fun `the native prefix is two ideographic spaces, and only for Chinese with the setting on`() {
        assertEquals("\u3000\u3000", paragraphIndentText(enabled = true, isChinese = true))
        assertEquals("", paragraphIndentText(enabled = false, isChinese = true))
        assertEquals("", paragraphIndentText(enabled = true, isChinese = false))
        assertEquals("", paragraphIndentText(enabled = false, isChinese = false))
    }

    @Test
    fun `the indent is exactly two full-width characters`() {
        val indent = paragraphIndentText(enabled = true, isChinese = true)
        assertEquals(2, indent.length)
        assertTrue(indent.all { it == '\u3000' })
    }

    @Test
    fun `the CSS indent is two em, and empty when there is nothing to indent`() {
        assertEquals("2em", paragraphIndentCss(enabled = true, isChinese = true))
        assertEquals("", paragraphIndentCss(enabled = false, isChinese = true))
        assertEquals("", paragraphIndentCss(enabled = true, isChinese = false))
        assertEquals("", paragraphIndentCss(enabled = false, isChinese = false))
    }

    @Test
    fun `both renderers agree on whether to indent`() {
        // The WebView path and the native path must never disagree: one verdict, two spellings.
        for (enabled in listOf(true, false)) {
            for (isChinese in listOf(true, false)) {
                val text = paragraphIndentText(enabled, isChinese)
                val css = paragraphIndentCss(enabled, isChinese)
                assertEquals(text.isNotEmpty(), css.isNotEmpty())
            }
        }
    }
}
