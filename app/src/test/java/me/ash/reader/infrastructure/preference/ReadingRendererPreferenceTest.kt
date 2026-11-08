package me.ash.reader.infrastructure.preference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingRendererPreferenceTest {

    @Test
    fun `the native component is the default renderer`() {
        // Product decision: on the target tablet the native LazyColumn path is noticeably
        // smoother than the WebView path. Reverting this silently undoes that choice.
        //
        // The argument order is deliberate: touching the nested object *first* is the JVM
        // class-initialization order that used to leave the companion's `default` field null.
        // Keeping it here makes this test a guard against that trap as well as the decision.
        assertSame(
            ReadingRendererPreference.NativeComponent,
            ReadingRendererPreference.default,
        )
    }

    @Test
    fun `the stored values did not move`() {
        // Users who explicitly picked WebView before the default changed keep their saved 0.
        assertEquals(0, ReadingRendererPreference.WebView.value)
        assertEquals(1, ReadingRendererPreference.NativeComponent.value)
    }

    @Test
    fun `both renderers are still offered in settings`() {
        assertEquals(2, ReadingRendererPreference.values.size)
        assertTrue(ReadingRendererPreference.values.contains(ReadingRendererPreference.WebView))
        assertTrue(
            ReadingRendererPreference.values.contains(ReadingRendererPreference.NativeComponent)
        )
    }
}
