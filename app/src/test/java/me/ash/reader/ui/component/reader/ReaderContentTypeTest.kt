package me.ash.reader.ui.component.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentTypeTest {

    @Test
    fun `every content type is a non blank string`() {
        ReaderContentType.all.forEach { value ->
            assertTrue("a blank contentType collapses back to no reuse", value.isNotBlank())
        }
    }

    @Test
    fun `no two item types share a content type`() {
        // If this ever goes red, two different rows are being handed the same composition.
        assertEquals(
            ReaderContentType.all.size,
            ReaderContentType.all.toSet().size,
        )
    }

    @Test
    fun `the full set is pinned`() {
        assertEquals(
            listOf(
                "reader_headline",
                "reader_footer",
                "reader_paragraph",
                "reader_code_block",
                "reader_image",
                "reader_caption",
                "reader_video",
            ),
            ReaderContentType.all,
        )
    }
}
