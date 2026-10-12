package me.ash.reader.ui.component.webview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.ash.reader.infrastructure.preference.LocalOpenLink
import me.ash.reader.infrastructure.preference.LocalOpenLinkSpecificBrowser
import me.ash.reader.infrastructure.preference.LocalReadingBoldCharacters
import me.ash.reader.infrastructure.preference.LocalReadingFonts
import me.ash.reader.infrastructure.preference.LocalReadingImageHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingImageRoundedCorners
import me.ash.reader.infrastructure.preference.LocalReadingPageTonalElevation
import me.ash.reader.infrastructure.preference.LocalReadingSubheadBold
import me.ash.reader.infrastructure.preference.LocalReadingSubheadUpperCase
import me.ash.reader.infrastructure.preference.LocalReadingTextAlign
import me.ash.reader.infrastructure.preference.LocalReadingTextBold
import me.ash.reader.infrastructure.preference.LocalReadingTextFontSize
import me.ash.reader.infrastructure.preference.LocalReadingTextHorizontalPadding
import me.ash.reader.infrastructure.preference.LocalReadingTextLetterSpacing
import me.ash.reader.infrastructure.preference.LocalReadingTextLineHeight
import me.ash.reader.infrastructure.preference.ReadingFontsPreference
import me.ash.reader.ui.ext.ExternalFonts
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.surfaceColorAtElevation
import me.ash.reader.ui.theme.palette.alwaysLight

@Composable
fun RYWebView(
    modifier: Modifier = Modifier,
    content: String,
    refererDomain: String? = null,
    onImageClick: ((imgUrl: String, altText: String) -> Unit)? = null,
    paragraphIndent: String = "",
) {
    val context = LocalContext.current
    val openLink = LocalOpenLink.current
    val openLinkSpecificBrowser = LocalOpenLinkSpecificBrowser.current
    val tonalElevation = LocalReadingPageTonalElevation.current
    val selectionTextColor = Color.Black.toArgb()
    val selectionBgColor = (MaterialTheme.colorScheme.tertiaryContainer alwaysLight true).toArgb()
    val textColor: Int = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val textBold: Boolean = LocalReadingTextBold.current.value
    val textAlign: String = LocalReadingTextAlign.current.toTextAlignCSS()
    val textMargin: Int = LocalReadingTextHorizontalPadding.current
    val boldTextColor: Int = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkTextColor: Int = MaterialTheme.colorScheme.primary.toArgb()
    val subheadBold: Boolean = LocalReadingSubheadBold.current.value
    val subheadUpperCase: Boolean = LocalReadingSubheadUpperCase.current.value
    val readingFonts = LocalReadingFonts.current
    val fontSize: Int = LocalReadingTextFontSize.current
    val letterSpacing: Float = LocalReadingTextLetterSpacing.current
    val lineHeight: Float = LocalReadingTextLineHeight.current
    val imgMargin: Int = LocalReadingImageHorizontalPadding.current
    val imgBorderRadius: Int = LocalReadingImageRoundedCorners.current
    val codeTextColor: Int = MaterialTheme.colorScheme.tertiary.toArgb()
    val codeBgColor: Int =
        MaterialTheme.colorScheme.surfaceColorAtElevation((tonalElevation.value + 6).dp).toArgb()
    val boldCharacters = LocalReadingBoldCharacters.current

    // One WebView for the lifetime of this composable. It used to be keyed on the surface
    // colour, which built a fresh instance on every theme change that AndroidView then threw
    // away - `factory` only ever runs once - so those instances were never attached to a
    // window and never destroyed. `onRelease` below is what actually frees this one.
    val webView =
        remember {
            WebViewLayout.get(
                context = context,
                readingFontsPreference = readingFonts,
                webViewClient =
                    WebViewClient(
                        context = context,
                        refererDomain = refererDomain,
                        onOpenLink = { url ->
                            context.openURL(url, openLink, openLinkSpecificBrowser)
                        },
                    ),
                onImageClick = onImageClick,
            )
        }

    val fontPath =
        if (readingFonts is ReadingFontsPreference.External)
            ExternalFonts.FontType.ReadingFont.toPath(context)
        else if (readingFonts is ReadingFontsPreference.GoogleSans) {
            "/android_res/font/google_sans_flex.ttf"
        } else null

    // The stylesheet and the selection script are pure functions of the settings above, so they
    // are rebuilt when one of those changes rather than on every recomposition.
    val stylesheet =
        remember(
            fontSize,
            fontPath,
            lineHeight,
            letterSpacing,
            textMargin,
            textColor,
            textBold,
            textAlign,
            boldTextColor,
            subheadBold,
            subheadUpperCase,
            imgMargin,
            imgBorderRadius,
            linkTextColor,
            codeTextColor,
            codeBgColor,
            selectionTextColor,
            selectionBgColor,
            paragraphIndent,
        ) {
            WebViewStyle.get(
                fontSize = fontSize,
                fontPath = fontPath,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textMargin = textMargin,
                textColor = textColor,
                textBold = textBold,
                textAlign = textAlign,
                boldTextColor = boldTextColor,
                subheadBold = subheadBold,
                subheadUpperCase = subheadUpperCase,
                imgMargin = imgMargin,
                imgBorderRadius = imgBorderRadius,
                linkTextColor = linkTextColor,
                codeTextColor = codeTextColor,
                codeBgColor = codeBgColor,
                tableMargin = textMargin,
                selectionTextColor = selectionTextColor,
                selectionBgColor = selectionBgColor,
                paragraphIndent = paragraphIndent,
            )
        }
    val script = remember(boldCharacters) { WebViewScript.get(boldCharacters.value) }

    // What the WebView was last asked to render. See [RenderedDocument]. A plain holder
    // rather than a Compose state, so writing to it from `update` cannot schedule a
    // recomposition of its own.
    val rendered = remember { RenderedDocument() }

    AndroidView(
        modifier = modifier,
        factory = { webView },
        onRelease = { it.destroy() },
    ) {
        it.apply {
            // `update` runs on every recomposition, and this composable recomposes on every
            // scroll-direction change, because the reading page drives its toolbar auto-hide
            // from that state. Reloading unconditionally used to re-parse and re-lay-out the
            // whole article each time, which is the most expensive thing this file can do.
            //
            // The document is a pure function of the four values compared below, so skipping the
            // reload when they are unchanged cannot alter what is on screen. `url` is the
            // WebView's own current address, which is what the <base href> needs; it is read
            // here rather than remembered because only the view knows it.
            val previous = rendered.document
            if (previous == null ||
                previous.stylesheet != stylesheet ||
                previous.script != script ||
                previous.content != content ||
                previous.baseUrl != it.url
            ) {
                rendered.document =
                    Rendered(
                        stylesheet = stylesheet,
                        script = script,
                        content = content,
                        baseUrl = it.url,
                    )
                settings.defaultFontSize = fontSize
                loadDataWithBaseURL(
                    null,
                    WebViewHtml.HTML.format(stylesheet, it.url, content, script),
                    "text/HTML",
                    "UTF-8",
                    null,
                )
            }
        }
    }
}

/**
 * Holds the document a [android.webkit.WebView] was last asked to render.
 *
 * Deliberately not Compose state: [update] writes to it, and a snapshot-backed holder would turn
 * every reload into another recomposition. `remember` already gives it the lifetime it needs.
 */
private class RenderedDocument(var document: Rendered? = null)

/**
 * The inputs behind the document currently loaded in a [android.webkit.WebView].
 *
 * [android.webkit.WebView.loadDataWithBaseURL] takes four arguments and produces a document that
 * depends on nothing else, so comparing these is the same test as comparing the rendered HTML -
 * without building that HTML string on every recomposition just to throw it away.
 */
private class Rendered(
    val stylesheet: String,
    val script: String,
    val content: String,
    val baseUrl: String?,
)