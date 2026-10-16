package cloud.samlo.couch

import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.Sections
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionsTest {
    private fun order(saved: List<String>, ids: List<String>) =
        Sections.order(ids, Config().apply { sectionOrder = saved })

    @Test
    fun noSavedOrderKeepsTheDefault() {
        assertEquals(listOf("apps", "inputs"), order(emptyList(), listOf("apps", "inputs")))
    }

    @Test
    fun savedOrderWins() {
        assertEquals(listOf("inputs", "apps"), order(listOf("inputs", "apps"), listOf("apps", "inputs")))
    }

    @Test
    fun newRowsGoLastInDefaultOrder() {
        assertEquals(
            listOf("inputs", "apps", "ch:1", "ch:2"),
            order(listOf("inputs", "apps"), listOf("apps", "inputs", "ch:1", "ch:2")),
        )
    }

    @Test
    fun staleIdsAreDropped() {
        assertEquals(listOf("apps"), order(listOf("ch:9", "apps"), listOf("apps")))
    }

    @Test
    fun channelIdsAreNamespaced() {
        assertEquals("ch:42", Sections.channelId(42))
    }
}
