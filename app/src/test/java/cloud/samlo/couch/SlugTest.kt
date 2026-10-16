package cloud.samlo.couch

import cloud.samlo.couch.data.slug
import org.junit.Assert.assertEquals
import org.junit.Test

class SlugTest {
    @Test
    fun keepsLowercaseLettersAndDigitsOnly() {
        assertEquals("hdmi4", slug("HDMI 4"))
        assertEquals("hdmi1arc", slug("HDMI 1 (ARC)"))
    }

    @Test
    fun dropsAccentsAndSymbols() {
        assertEquals("tl", slug("Télé"))
        assertEquals("", slug("¤ — ·"))
    }

    @Test
    fun isStableForTheSameName() {
        assertEquals(slug("Composite"), slug("composite "))
    }
}
