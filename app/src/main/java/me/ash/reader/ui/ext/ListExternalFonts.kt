package me.ash.reader.ui.ext

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Imported TTF files for the font settings that are not the app-wide or the reading body font, one
 * slot each.
 *
 * Deliberately separate from [ExternalFonts], which the app theme and the reading body text already
 * use. Reusing those would have meant:
 *
 *  - the per-page settings could not have a font of their own - picking one for the feeds list would
 *    silently repaint the reading page, because `ExternalFonts.FontType` is keyed by *purpose*
 *    (basic / reading), not by setting; and
 *  - importing would keep forcing an app restart (`context.restart()`), which is how that path
 *    invalidates its static `Typography` cache. The [generations] counter below is the invalidation
 *    instead, so a re-import takes effect on the spot.
 *
 * One slot per setting, rather than one per page: the user asked for each font row to import its own
 * file, so a page whose title and summary are different fonts has to be able to hold both.
 *
 * A row with nothing imported falls back to its page's own font, which is what
 * `TitleFontsPreference.Follow` means - so a reader who had only ever imported one file per page
 * keeps seeing that file after the settings multiply. That fallback lives at the *call site*
 * (`ListFonts.kt`), not here: it is a choice about which preference to consult next, and the reading
 * page's title falls back to a font this object does not own.
 *
 * This file is new, so it costs nothing on an upstream merge.
 */
object ListExternalFonts {

    /**
     * One importable font per font setting. The file name is what actually lands in `filesDir`, and
     * it is also the key [FontNames] stores the display name under.
     *
     * The first two names predate this enum - the feeds and flow pages already wrote
     * `feeds_font.ttf` and `flow_font.ttf` before each row got a slot of its own - so anyone who
     * imported a font back then still has it.
     */
    enum class Slot(val fileName: String) {
        Feeds("feeds_font.ttf"),
        Flow("flow_font.ttf"),
        FlowTitle("flow_title_font.ttf"),
        FlowSummary("flow_summary_font.ttf"),
        ReadingTitle("reading_title_font.ttf"),
    }

    /**
     * Bumped on every import, read from composition.
     *
     * This is what makes a re-import of the *same* slot take effect without an app restart. The
     * preference value does not change when the user imports a second file into a slot that already
     * says `External`, so nothing would otherwise invalidate the cached [FontFamily].
     */
    private val generations = mutableStateMapOf<Slot, Int>()

    /** Cached because [Typeface.createFromFile] hits the disk and is called per list row. */
    private val cache = mutableMapOf<Slot, FontFamily?>()

    /** Read this inside a composable to recompose when the slot's font is replaced. */
    @Composable
    fun generation(slot: Slot): Int = generations[slot] ?: 0

    /** Whether the slot has a font of its own, i.e. whether this row is on an imported font. */
    fun hasFont(context: Context, slot: Slot): Boolean = file(context, slot).exists()

    /** Copies the picked document into the slot, replacing any previous font. */
    fun import(context: Context, uri: Uri, slot: Slot) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        file(context, slot).let {
            if (it.exists()) it.delete()
            if (it.createNewFile()) it.writeBytes(bytes)
        }
        // Parsed from the bytes already in hand rather than by re-reading the document.
        FontNames.record(context, slot.fileName, bytes)
        cache.remove(slot)
        generations[slot] = (generations[slot] ?: 0) + 1
    }

    /** The slot's font, or `null` if nothing has been imported into it yet. */
    fun load(context: Context, slot: Slot): FontFamily? =
        cache.getOrPut(slot) {
            file(context, slot).takeIf { it.exists() }?.let { FontFamily(Typeface.createFromFile(it)) }
        }

    private fun file(context: Context, slot: Slot): File =
        File(context.filesDir.absolutePath + File.separator + slot.fileName)
}
