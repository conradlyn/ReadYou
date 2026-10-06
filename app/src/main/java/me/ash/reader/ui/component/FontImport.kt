package me.ash.reader.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import me.ash.reader.R
import me.ash.reader.ui.component.base.RadioDialogOption
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.FontNames
import java.io.File

/**
 * The last row of every font dialog: "Import font", with the imported font's own name underneath.
 *
 * The name is the point. The app now has seven font rows, each importing its own file, and a dialog
 * that only said "Import font" would leave the user guessing which of their files a row is on. The
 * name is read out of the font file itself by [FontNames], so it is the font's own idea of what it
 * is called rather than the file name the user happened to download.
 *
 * These helpers are shared by all seven dialogs so that the behaviour - including the parts that are
 * easy to get subtly wrong, like staying open and re-reading the name - is defined once.
 */

/**
 * The name the imported font calls itself, or `null` when nothing is imported for [fileName] or the
 * file does not state a usable name.
 *
 * [refreshKey] is how the name becomes visible the moment it is imported. The name is remembered in
 * a sidecar file rather than held in memory, so it has to be invalidated explicitly: the preference
 * value does not change when a second file replaces the first in a row that already says "Import
 * font", so nothing else would tell composition to look again. Callers pass whatever changes on
 * import - the page's own counter, or `ListExternalFonts.generation` where one is already read.
 */
@Composable
fun importedFontName(fileName: String, refreshKey: Int): String? {
    val context = LocalContext.current
    return remember(context, fileName, refreshKey) { FontNames.of(context, fileName) }
}

/**
 * Whether one of the two app-wide fonts (basic / reading) has a file in internal storage.
 *
 * Those two predate [me.ash.reader.ui.ext.ListExternalFonts] and still live in [ExternalFonts], so
 * they need their own presence check.
 */
fun hasImportedFont(context: Context, type: ExternalFonts.FontType): Boolean =
    File(type.toPath(context)).exists()

/**
 * The "Import font" row.
 *
 * [selected] is whether the row is the current choice, [imported] whether a file is already there,
 * [name] what it calls itself, and [fontFamily] the font to preview it in.
 *
 * Always the last option: [me.ash.reader.infrastructure.preference.ListFontsPreference] and
 * [me.ash.reader.infrastructure.preference.TitleFontsPreference] both keep `External` at the end of
 * their `values`, so building the row from the enum ordering already puts it at the bottom.
 */
@Composable
fun importFontOption(
    selected: Boolean,
    imported: Boolean,
    name: String?,
    fontFamily: FontFamily?,
    onImport: () -> Unit,
): RadioDialogOption =
    RadioDialogOption(
        text = stringResource(R.string.import_font),
        // "Imported" rather than the name when the file is there but does not name itself, so that
        // a font the parser could not read stays distinguishable from no font at all.
        subtitle =
            when {
                name != null -> name
                imported -> stringResource(R.string.imported)
                else -> stringResource(R.string.not_imported)
            },
        // Only previewed once a file is actually there. Before that the resolved family is a
        // fallback - the system font, or the page font - and drawing this one row in it would make
        // the row stop matching the rest of the dialog for no reason.
        style = fontFamily?.takeIf { imported }?.let { TextStyle(fontFamily = it) },
        selected = selected,
        // Deliberately does not close the dialog. This row exists to show the name appear once the
        // picker returns, and a dialog that dismissed on tap would hide exactly that.
        dismissOnClick = false,
        onClick = onImport,
    )
