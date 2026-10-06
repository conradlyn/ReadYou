package me.ash.reader.ui.ext

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the sfnt `name` table reader, which is the part of the font-name feature that has no
 * compiler and no device to catch it.
 *
 * The file is built here rather than checked in as a fixture: a font that says exactly what a test
 * needs is thirty lines of big-endian writes, and it keeps the assertions honest about *why* a name
 * was chosen instead of only *that* it was. The cases that matter are the orderings, because each
 * one is a silent fallback:
 *
 *  - typographic family (16) over legacy family (1) over full name (4);
 *  - the requested language over the rest;
 *  - Windows UTF-16BE over the Macintosh single-byte encodings.
 *
 * Dropping any of them still returns *a* name, just the wrong one - which is exactly the kind of
 * bug that ships. `familyName` is a pure function over `ByteArray` for this reason, the same split
 * as `scaledDp` and `shouldLeaveAfterMarkAsRead`.
 */
class FontNamesTest {

    @Test
    fun `the typographic family wins over the legacy family and the full name`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "Legacy"),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 4, text = "Full Bold"),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 16, text = "Typographic"),
            )
        assertEquals("Typographic", FontNames.familyName(bytes))
    }

    @Test
    fun `the legacy family is used when there is no typographic family`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 4, text = "Full Bold"),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "Legacy"),
            )
        assertEquals("Legacy", FontNames.familyName(bytes))
    }

    @Test
    fun `the full name is the last resort`() {
        val bytes =
            buildFont(record(platform = WINDOWS, language = LANG_EN_US, nameId = 4, text = "Full"))
        assertEquals("Full", FontNames.familyName(bytes))
    }

    @Test
    fun `the requested language wins over file order`() {
        // zh-CN is listed *first* in the file, so the two orderings can be told apart: asking for
        // en-US has to reach past the earlier record. The two records deliberately carry different
        // text - with the same text in both, this test could not fail.
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_ZH_CN, nameId = 16, text = "思源宋体"),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 16, text = "Source Han Serif SC"),
            )
        assertEquals(
            "Source Han Serif SC",
            FontNames.familyName(bytes, preferredLanguages = intArrayOf(LANG_EN_US, LANG_ZH_CN)),
        )
        // The default order is zh-CN first, which is what a Chinese reader recognises; en-US is the
        // fallback for the fonts that ship no Chinese record.
        assertEquals("思源宋体", FontNames.familyName(bytes))
    }

    @Test
    fun `a language that was not asked for is still better than nothing`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = 0x0407, nameId = 16, text = "Deutsch"),
            )
        assertEquals("Deutsch", FontNames.familyName(bytes, preferredLanguages = intArrayOf(LANG_ZH_CN)))
    }

    @Test
    fun `a CJK name survives the UTF-16BE decode`() {
        // Two-byte-per-character is the whole reason the Windows branch exists: a Macintosh record
        // cannot express this name at all, and a byte-per-character decode would mangle it.
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_ZH_CN, nameId = 16, text = "思源宋体"),
            )
        assertEquals("思源宋体", FontNames.familyName(bytes))
    }

    @Test
    fun `a CJK name is preferred over the Latin one when it is asked for`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 16, text = "Source Han Serif SC"),
                record(platform = WINDOWS, language = LANG_ZH_CN, nameId = 16, text = "思源宋体"),
            )
        assertEquals("思源宋体", FontNames.familyName(bytes))
        assertEquals(
            "Source Han Serif SC",
            FontNames.familyName(bytes, preferredLanguages = intArrayOf(LANG_EN_US)),
        )
    }

    @Test
    fun `an OTTO container is read like a TrueType one`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "CffFont"),
                sfntVersion = 0x4F54544F,
            )
        assertEquals("CffFont", FontNames.familyName(bytes))
    }

    @Test
    fun `a collection is read from its first font`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "First"),
                asCollection = true,
            )
        assertEquals("First", FontNames.familyName(bytes))
    }

    @Test
    fun `a Macintosh-only record is still read`() {
        val bytes =
            buildFont(
                record(platform = MACINTOSH, language = 0, nameId = 1, text = "MacOnly"),
            )
        assertEquals("MacOnly", FontNames.familyName(bytes))
    }

    @Test
    fun `Windows wins over Macintosh even when Macintosh is listed first`() {
        val bytes =
            buildFont(
                record(platform = MACINTOSH, language = 0, nameId = 1, text = "MacName"),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "WinName"),
            )
        assertEquals("WinName", FontNames.familyName(bytes))
    }

    @Test
    fun `a blank name is not a name`() {
        val bytes =
            buildFont(
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 16, text = "   "),
                record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "Real"),
            )
        assertEquals("Real", FontNames.familyName(bytes))
    }

    @Test
    fun `a file that is not a font is refused`() {
        assertNull(FontNames.familyName("this is not a font, it is a text file".toByteArray()))
        assertNull(FontNames.familyName(ByteArray(0)))
        assertNull(FontNames.familyName(ByteArray(8)))
    }

    @Test
    fun `a truncated file is refused instead of throwing`() {
        // A real font, cut off in the middle of the name table. Every offset read past the end has
        // to come back as "no name" rather than as an exception - the file came from outside the
        // app, and this runs on the settings page's composition.
        val full = buildFont(record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "Cut"))
        for (length in 0 until full.size) {
            assertNull(
                "truncating to $length bytes should not produce a name",
                FontNames.familyName(full.copyOf(length)),
            )
        }
    }

    @Test
    fun `a name table that claims to run past the end of the file is refused`() {
        val bytes = buildFont(record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "Liar"))
        // Inflate the `length` field of the single table record (at 12 + 12, the 4th word).
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(12 + 12, Int.MAX_VALUE)
        assertNull(FontNames.familyName(bytes))
    }

    @Test
    fun `a font with no name table at all is refused`() {
        val bytes = buildFont(record(platform = WINDOWS, language = LANG_EN_US, nameId = 1, text = "X"))
        // Rename the table so the walk finds nothing.
        ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putInt(12, 0x6F746865) // 'othe'
        assertNull(FontNames.familyName(bytes))
    }

    // --- the file builder ---

    private data class NameRecordSpec(
        val platform: Int,
        val language: Int,
        val nameId: Int,
        val text: String,
    )

    private fun record(platform: Int, language: Int, nameId: Int, text: String) =
        NameRecordSpec(platform, language, nameId, text)

    /**
     * Writes the smallest sfnt that [FontNames] will accept: one table, `name`, holding [records].
     *
     * Layout: offset table (12) + one table record (16) + name table (6 + 12n + strings). A
     * collection prepends a `ttcf` header and points at the same block.
     */
    private fun buildFont(vararg records: NameRecordSpec, sfntVersion: Int = 0x00010000, asCollection: Boolean = false): ByteArray {
        val encoded = records.map { encode(it) }
        val headerSize = 6
        val recordSize = 12
        val nameTableLength = headerSize + recordSize * records.size + encoded.sumOf { it.size }

        val collectionHeader = if (asCollection) 16 else 0
        val nameTableOffset = collectionHeader + 12 + 16
        val total = nameTableOffset + nameTableLength

        val buffer = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
        if (asCollection) {
            buffer.putInt(0x74746366) // 'ttcf'
            buffer.putInt(0x00010000)
            buffer.putInt(1) // one font
            buffer.putInt(collectionHeader) // where that font starts
        }
        buffer.putInt(sfntVersion)
        buffer.putShort(1) // one table
        buffer.putShort(0) // searchRange
        buffer.putShort(0) // entrySelector
        buffer.putShort(0) // rangeShift
        buffer.putInt(0x6E616D65) // 'name'
        buffer.putInt(0) // checksum
        buffer.putInt(nameTableOffset)
        buffer.putInt(nameTableLength)

        buffer.putShort(0) // format
        buffer.putShort(records.size.toShort()) // count
        buffer.putShort((headerSize + recordSize * records.size).toShort()) // stringOffset
        var stringOffset = 0
        encoded.forEachIndexed { index, bytes ->
            buffer.putShort(records[index].platform.toShort())
            buffer.putShort(0) // encodingID
            buffer.putShort(records[index].language.toShort())
            buffer.putShort(records[index].nameId.toShort())
            buffer.putShort(bytes.size.toShort())
            buffer.putShort(stringOffset.toShort())
            stringOffset += bytes.size
        }
        encoded.forEach { buffer.put(it) }
        return buffer.array()
    }

    private fun encode(spec: NameRecordSpec): ByteArray =
        when (spec.platform) {
            WINDOWS, UNICODE -> spec.text.toByteArray(StandardCharsets.UTF_16BE)
            else -> spec.text.toByteArray(StandardCharsets.ISO_8859_1)
        }

    private companion object {
        const val WINDOWS = 3
        const val UNICODE = 0
        const val MACINTOSH = 1
        const val LANG_EN_US = 0x0409
        const val LANG_ZH_CN = 0x0804
    }
}
