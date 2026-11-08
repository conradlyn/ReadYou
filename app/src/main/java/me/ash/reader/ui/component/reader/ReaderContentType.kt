package me.ash.reader.ui.component.reader

/**
 * `contentType` values for every `item {}` emitted on the native-renderer path.
 *
 * `LazyColumn` only reuses a composition when the item being recycled has the same contentType
 * as the one taking its place. Without these, every item reports `null`, so a paragraph
 * composition can be handed to an image (or vice versa) and has to be composed and measured from
 * scratch on every scroll.
 *
 * The values must stay distinct: a collision silently puts the wrong composition behind an item,
 * and neither the compiler nor a recomposition count will catch it. `ReaderContentTypeTest`
 * pins both the full set and its uniqueness.
 */
object ReaderContentType {
    const val Headline = "reader_headline"
    const val Footer = "reader_footer"
    const val Paragraph = "reader_paragraph"
    const val CodeBlock = "reader_code_block"
    const val Image = "reader_image"
    const val Caption = "reader_caption"
    const val Video = "reader_video"

    /** Every value, in declaration order. */
    val all: List<String> = listOf(
        Headline,
        Footer,
        Paragraph,
        CodeBlock,
        Image,
        Caption,
        Video,
    )
}
