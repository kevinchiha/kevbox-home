package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.home.ColumnMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColumnMoveTest {
    @Test
    fun shiftClampsAtBothEnds() {
        val first = ColumnMove(listOf("a", "b", "c"), "a")
        assertFalse(first.shift(-1))
        assertEquals(listOf("a", "b", "c"), first.order)
        assertEquals(0, first.index)

        val last = ColumnMove(listOf("a", "b", "c"), "c")
        assertFalse(last.shift(1))
        assertEquals(listOf("a", "b", "c"), last.order)
        assertEquals(2, last.index)
    }

    @Test
    fun shiftMovesTheAppAndItsIndexFollows() {
        val move = ColumnMove(listOf("a", "b", "c"), "a")
        assertTrue(move.shift(1))
        assertEquals(listOf("b", "a", "c"), move.order)
        assertEquals(1, move.index)
    }

    @Test
    fun savedKeepsOtherPackagesInTheirSlots() {
        val move = ColumnMove(listOf("a", "b", "c"), "c")
        move.shift(-1)
        move.shift(-1)
        // x is in config.apps but not in the column (hidden or uninstalled): it keeps slot 1.
        assertEquals(listOf("c", "x", "a", "b"), move.saved(listOf("a", "x", "b", "c")))
    }

    @Test
    fun savedWithoutAShiftReturnsAppsUnchanged() {
        val move = ColumnMove(listOf("a", "b", "c"), "b")
        val apps = listOf("a", "x", "b", "y", "c")
        assertEquals(apps, move.saved(apps))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aPackageNotInTheColumnIsRejected() {
        ColumnMove(listOf("a", "b", "c"), "z")
    }
}
