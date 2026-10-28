package cloud.samlo.couch.kevbox

import cloud.samlo.couch.kevbox.drawer.GridMove
import org.junit.Assert.assertEquals
import org.junit.Test

class GridMoveTest {
    @Test
    fun stepsInsideTheGrid() {
        assertEquals(7, GridMove.target(6, 1, 0, 12))
        assertEquals(5, GridMove.target(6, -1, 0, 12))
        assertEquals(1, GridMove.target(6, 0, -1, 12))
        assertEquals(11, GridMove.target(6, 0, 1, 12))
    }

    @Test
    fun edgesHold() {
        assertEquals(4, GridMove.target(4, 1, 0, 12))
        // No wrap to the end of the previous row.
        assertEquals(5, GridMove.target(5, -1, 0, 12))
        assertEquals(2, GridMove.target(2, 0, -1, 12))
        assertEquals(11, GridMove.target(11, 0, 1, 12))
    }

    @Test
    fun downIntoAShortLastRowLandsOnTheLastItem() {
        assertEquals(11, GridMove.target(8, 0, 1, 12))
        assertEquals(11, GridMove.target(11, 1, 0, 12))
    }

    @Test
    fun emptyOrOutOfRangeInputReturnsFrom() {
        assertEquals(0, GridMove.target(0, 1, 0, 0))
        assertEquals(7, GridMove.target(7, 0, 1, 3))
    }
}
