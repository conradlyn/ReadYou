package me.ash.reader.ui.ext

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

/**
 * Imported TTF files for the two list pages, one slot each.
 *
 * Deliberately separate from [ExternalFonts], which the reading page and the app theme already use.
 * Reusing those would have meant:
 *
 *  - the list pages could not have a font of their own - picking one for the feeds list would
 *    silently repaint the reading page, because `ExternalFonts.FontType` is keyed by *purpose*
 *    (basic / reading), not by page; and
 *  - the reading page's import would keep forcing an app restart (`context.restart()`), which is
 *    how that path invalidates its static `Typography` cache.
 *
 * This file is new, so it costs nothing on an upstream merge, and it keeps the two list pages
 * independent of each other and of the reading page.
 */
object ListExternalFonts {

    /** One importable font per list page. The file name is what actually lands in `filesDir`. */
    enum class Slot(val fileName: String) {
        Feeds("feeds_font.ttf"),
        Flow("flow_font.ttf"),
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

    /** Whether the slot has a font to offer, i.e. whether importing it is worth offering. */
    fun hasFont(context: Context, slot: Slot): Boolean = file(context, slot).exists()

    /** Copies the picked document into the slot, replacing any previous font. */
    fun import(context: Context, uri: Uri, slot: Slot) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        file(context, slot).let {
            if (it.exists()) it.delete()
            if (it.createNewFile()) it.writeBytes(bytes)
        }
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
