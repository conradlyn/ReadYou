package me.ash.reader.ui.ext

import android.content.Context
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * The name a font file gives itself, and where that name is remembered between launches.
 *
 * A settings row can only say "Imported" about a font the user picked weeks ago, which is no help
 * when seven different rows each have their own imported file - the point of showing the name is
 * that the user recognises *which* font this row is on without opening the file picker again.
 *
 * Two halves, deliberately separate:
 *
 *  - [familyName] is a pure function over the file's bytes. It reads the `name` table out of the
 *    sfnt container, which is where a TTF or OTF states its own family name. No Android types, so
 *    it is unit-testable on a plain JVM against synthetic bytes.
 *  - [record] / [of] persist that name in a small sidecar file next to the font, so it survives a
 *    restart without re-parsing a multi-megabyte file every time a settings page composes.
 *
 * Nothing here knows which setting a font belongs to. Callers pass the file name, which is what
 * lets the two mechanisms in this app - [ExternalFonts] for the app-wide and reading fonts, and
 * [ListExternalFonts] for the list and title fonts - share one implementation.
 */
object FontNames {

    /** `name` table tag, `'name'`. */
    private const val TAG_NAME = 0x6E616D65

    /** `ttcf`, the container that holds several fonts in one file. */
    private const val TAG_TTCF = 0x74746366

    /** `'OTTO'`, the CFF-flavoured sfnt. */
    private const val TAG_OTTO = 0x4F54544F

    /** `0x00010000`, the TrueType-flavoured sfnt. */
    private const val TAG_TRUETYPE = 0x00010000

    private const val NAME_ID_FAMILY = 1
    private const val NAME_ID_FULL = 4
    private const val NAME_ID_TYPOGRAPHIC_FAMILY = 16

    private const val PLATFORM_UNICODE = 0
    private const val PLATFORM_MACINTOSH = 1
    private const val PLATFORM_WINDOWS = 3

    /** `0x0409`, en-US. The one language almost every font carries. */
    private const val LANG_EN_US = 0x0409

    /** `0x0804`, zh-CN. Present on most CJK fonts, and the name a Chinese reader recognises. */
    private const val LANG_ZH_CN = 0x0804

    /**
     * The family name of the font in [bytes], or `null` if the file is not a parseable sfnt or
     * carries no usable name record.
     *
     * [preferredLanguages] is a priority list of Windows language IDs. The typographic family
     * (`nameID` 16) wins over the legacy family (`nameID` 1), which wins over the full name
     * (`nameID` 4) - the same order font managers use, because 16 is the one that survives
     * sub-family splitting.
     *
     * Every read is bounds-checked and every failure returns `null`. A font picked through
     * `MimeType.FONT` is still a file from outside the app, and a truncated or exotic one must
     * degrade to "no name shown" rather than take the settings page down.
     */
    fun familyName(
        bytes: ByteArray,
        preferredLanguages: IntArray = intArrayOf(LANG_ZH_CN, LANG_EN_US),
    ): String? {
        val records = nameRecords(bytes) ?: return null
        for (nameId in intArrayOf(NAME_ID_TYPOGRAPHIC_FAMILY, NAME_ID_FAMILY, NAME_ID_FULL)) {
            val candidates = records.filter { it.nameId == nameId }
            if (candidates.isEmpty()) continue

            // Windows records are UTF-16BE and cover every language the font ships; Macintosh ones
            // are a single legacy encoding. So the platform order is not a preference, it is a
            // correctness ordering - only fall back to Macintosh if Windows said nothing.
            val windows = candidates.filter { it.platformId == PLATFORM_WINDOWS }
            val ordered =
                preferredLanguages.flatMap { language ->
                    windows.filter { it.languageId == language }
                } + windows.filter { it.languageId !in preferredLanguages } +
                    candidates.filter { it.platformId == PLATFORM_UNICODE } +
                    candidates.filter { it.platformId == PLATFORM_MACINTOSH }

            ordered.firstNotNullOfOrNull { decode(bytes, it)?.takeIf { name -> name.isNotBlank() } }
                ?.let {
                    return it
                }
        }
        return null
    }

    /**
     * Parses [uri] and remembers the result for [fileName].
     *
     * Used by the fonts that go through [ExternalFonts], whose import path does not hand out the
     * bytes. The file is read a second time; it happens once per import, next to a copy of the same
     * bytes, so it is not worth reshaping that class to avoid it.
     */
    fun record(context: Context, fileName: String, uri: Uri) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        record(context, fileName, bytes)
    }

    /** Parses [bytes] and remembers the result for [fileName]. */
    fun record(context: Context, fileName: String, bytes: ByteArray?) {
        val name = bytes?.let { familyName(it) }
        runCatching { sidecar(context, fileName).writeText(name.orEmpty()) }
    }

    /**
     * The name remembered for [fileName], or `null` when nothing is imported or no name was found.
     *
     * Reads a sidecar that is a few dozen bytes, on purpose: the alternative is caching it in a
     * `mutableStateMapOf` and writing to that map from composition, which is how a settings row
     * ends up recomposing itself in a loop.
     */
    fun of(context: Context, fileName: String): String? {
        if (!fontFile(context, fileName).exists()) return null
        return runCatching { sidecar(context, fileName).takeIf { it.exists() }?.readText() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun fontFile(context: Context, fileName: String): File =
        File(context.filesDir.absolutePath + File.separator + fileName)

    private fun sidecar(context: Context, fileName: String): File =
        File(context.filesDir.absolutePath + File.separator + fileName + ".name")

    private data class NameRecord(
        val platformId: Int,
        val languageId: Int,
        val nameId: Int,
        val offset: Int,
        val length: Int,
    )

    private fun decode(bytes: ByteArray, record: NameRecord): String? {
        if (record.offset < 0 || record.length <= 0) return null
        if (record.offset + record.length > bytes.size) return null
        return runCatching {
            when (record.platformId) {
                PLATFORM_WINDOWS, PLATFORM_UNICODE ->
                    String(bytes, record.offset, record.length, StandardCharsets.UTF_16BE)

                else -> String(bytes, record.offset, record.length, StandardCharsets.ISO_8859_1)
            }
                .trim()
                .trimEnd('\u0000')
                .trim()
        }.getOrNull()
    }

    /**
     * Walks the sfnt table directory and returns the `name` table's records.
     *
     * `null` for anything that is not a single-font sfnt we understand. A `ttcf` collection is read
     * from its first font, which is the one a picker would preview.
     */
    private fun nameRecords(bytes: ByteArray): List<NameRecord>? {
        if (bytes.size < 12) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        val base =
            when (buffer.getInt(0)) {
                TAG_TRUETYPE, TAG_OTTO -> 0
                // A collection is read from its first font, which is the one a picker previews.
                TAG_TTCF -> {
                    if (bytes.size < 16) return null
                    buffer.getInt(12)
                }
                // Anything else is not sfnt: a plain text file, a WOFF wrapper, a zip.
                else -> return null
            }
        if (base < 0 || base + 12 > bytes.size) return null

        val tableCount = buffer.getShort(base + 4).toInt() and 0xFFFF
        for (index in 0 until tableCount) {
            val record = base + 12 + index * 16
            if (record + 16 > bytes.size) return null
            if (buffer.getInt(record) != TAG_NAME) continue

            val tableOffset = buffer.getInt(record + 8).toLong() and 0xFFFFFFFFL
            val tableLength = buffer.getInt(record + 12).toLong() and 0xFFFFFFFFL
            if (tableOffset + tableLength > bytes.size) return null
            return parseNameTable(bytes, buffer, tableOffset.toInt(), tableLength.toInt())
        }
        return null
    }

    private fun parseNameTable(
        bytes: ByteArray,
        buffer: ByteBuffer,
        tableOffset: Int,
        tableLength: Int,
    ): List<NameRecord>? {
        if (tableLength < 6 || tableOffset + 6 > bytes.size) return null

        val count = buffer.getShort(tableOffset + 2).toInt() and 0xFFFF
        val stringOffset = buffer.getShort(tableOffset + 4).toInt() and 0xFFFF
        val storage = tableOffset + stringOffset

        val records = ArrayList<NameRecord>(count)
        for (index in 0 until count) {
            val record = tableOffset + 6 + index * 12
            if (record + 12 > bytes.size) break
            val length = buffer.getShort(record + 8).toInt() and 0xFFFF
            val offset = storage + (buffer.getShort(record + 10).toInt() and 0xFFFF)
            records +=
                NameRecord(
                    platformId = buffer.getShort(record).toInt() and 0xFFFF,
                    languageId = buffer.getShort(record + 4).toInt() and 0xFFFF,
                    nameId = buffer.getShort(record + 6).toInt() and 0xFFFF,
                    offset = offset,
                    length = length,
                )
        }
        return records.ifEmpty { null }
    }
}
