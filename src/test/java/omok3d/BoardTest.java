package omok3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardTest {
    Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    /** Helper to place a stone and automatically decline Pie swap if move 1 just happened. */
    int placeWithDefaultPie(int x, int z, int player) {
        int y = board.place(x, z, player);
        if (board.isPieChoicePending()) {
            board.decidePie(false);
        }
        return y;
    }

    /** Stacks `height` white stones in column (x, z) to build a support. */
    void fill(int x, int z, int height) {
        for (int i = 0; i < height; i++) placeWithDefaultPie(x, z, 2);
    }

    // ---------- pie rule ----------

    @Test
    void pieRuleTriggersAfterFirstMoveAndBlocksSecondMoveUntilDecided() {
        assertFalse(board.isPieChoicePending());
        assertEquals(0, board.place(9, 9, 1));
        assertTrue(board.isPieChoicePending());

        // Cannot place second stone before deciding Pie Rule
        assertEquals(-1, board.place(9, 10, 2));

        // Player 2 decides to swap (takes Black)
        board.decidePie(true);
        assertFalse(board.isPieChoicePending());
        assertTrue(board.isPieSwapped());
        assertEquals(2, board.getCurrentTurn());

        // Now second stone (White) can be placed
        assertEquals(0, board.place(9, 10, 2));
    }

    @Test
    void pieRuleKeepSideWorks() {
        board.place(0, 0, 1);
        assertTrue(board.isPieChoicePending());
        board.decidePie(false);
        assertFalse(board.isPieChoicePending());
        assertFalse(board.isPieSwapped());
        assertEquals(0, board.place(1, 0, 2));
    }

    @Test
    void decidePieThrowsWhenNotPending() {
        assertThrows(IllegalStateException.class, () -> board.decidePie(true));
        board.place(0, 0, 1);
        board.decidePie(false);
        assertThrows(IllegalStateException.class, () -> board.decidePie(false));
    }

    // ---------- gravity ----------

    @Test
    void stonesStackFromTheBottomLayer() {
        assertEquals(0, placeWithDefaultPie(3, 4, 1));
        assertEquals(1, placeWithDefaultPie(3, 4, 2));
        assertEquals(2, placeWithDefaultPie(3, 4, 1));
        assertEquals(1, board.get(3, 0, 4));
        assertEquals(2, board.get(3, 1, 4));
        assertEquals(1, board.get(3, 2, 4));
        assertEquals(0, board.get(3, 3, 4));
    }

    @Test
    void otherColumnsAreUnaffected() {
        placeWithDefaultPie(3, 4, 1);
        assertEquals(0, board.dropY(4, 4));
        assertEquals(1, board.dropY(3, 4));
    }

    @Test
    void cannotPlaceOutOfRangeOrInFullColumn() {
        assertEquals(-1, placeWithDefaultPie(-1, 0, 1));
        assertEquals(-1, placeWithDefaultPie(0, Board.N, 1));
        for (int i = 0; i < Board.N; i++) assertEquals(i, placeWithDefaultPie(0, 0, i % 2 + 1));
        assertEquals(-1, board.dropY(0, 0));
        assertEquals(-1, placeWithDefaultPie(0, 0, 1));
    }

    @Test
    void removeTopUndoesTheLastStoneOfAColumn() {
        assertEquals(-1, board.removeTop(2, 2));
        placeWithDefaultPie(2, 2, 1);
        placeWithDefaultPie(2, 2, 2);
        assertEquals(1, board.removeTop(2, 2));
        assertEquals(0, board.get(2, 1, 2));
        assertEquals(1, board.get(2, 0, 2));
        assertEquals(1, board.dropY(2, 2));
        assertTrue(board.isPieChoicePending());
    }

    @Test
    void removeTopWorksOnAFullColumn() {
        for (int i = 0; i < Board.N; i++) placeWithDefaultPie(0, 0, 1);
        assertEquals(Board.N - 1, board.removeTop(0, 0));
        assertEquals(Board.N - 1, board.dropY(0, 0));
    }

    // ---------- winning ----------

    @Test
    void noStonesMeansNoWin() {
        assertNull(board.findWin(1));
        assertNull(board.findWin(2));
    }

    @Test
    void fiveInARowOnTheFloorWins() {
        for (int x = 0; x < 5; x++) placeWithDefaultPie(x, 7, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertEquals(5, win.stones().size());
        assertArrayEquals(new int[]{1, 0, 0}, win.dir());
    }

    @Test
    void fiveInARowAlongDepthWins() {
        for (int z = 3; z < 8; z++) placeWithDefaultPie(5, z, 1);
        assertNotNull(board.findWin(1));
    }

    @Test
    void fiveStackedInOneColumnWins() {
        for (int i = 0; i < 5; i++) placeWithDefaultPie(9, 9, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{0, 1, 0}, win.dir());
    }

    @Test
    void floorDiagonalWins() {
        for (int i = 0; i < 5; i++) placeWithDefaultPie(i, i, 1);
        assertNotNull(board.findWin(1));
    }

    @Test
    void verticalPlaneDiagonalWins() {
        for (int i = 0; i < 5; i++) {
            fill(i, 0, i);
            placeWithDefaultPie(i, 0, 1);
        }
        assertNull(board.findWin(2));
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{1, 1, 0}, win.dir());
    }

    @Test
    void spaceDiagonalWins() {
        for (int i = 0; i < 5; i++) {
            fill(i, i, i);
            placeWithDefaultPie(i, i, 1);
        }
        assertNull(board.findWin(2));
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertArrayEquals(new int[]{1, 1, 1}, win.dir());
    }

    @Test
    void projectionWithoutConnectionIsNotAWin() {
        // x is consecutive but heights are scattered: looks like a row from the front, but not connected
        int[] heights = {0, 3, 1, 4, 2};
        for (int i = 0; i < 5; i++) {
            fill(i, 0, heights[i]);
            placeWithDefaultPie(i, 0, 1);
        }
        assertNull(board.findWin(1));
    }

    @Test
    void gapOrOpponentStoneBreaksTheLine() {
        placeWithDefaultPie(0, 0, 1);
        placeWithDefaultPie(1, 0, 1);
        placeWithDefaultPie(2, 0, 2);
        placeWithDefaultPie(3, 0, 1);
        placeWithDefaultPie(4, 0, 1);
        assertNull(board.findWin(1));

        board.clear();
        for (int x : new int[]{0, 1, 3, 4, 5}) placeWithDefaultPie(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void fourIsNotAWin() {
        for (int x = 0; x < 4; x++) placeWithDefaultPie(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void lineDoesNotWrapAroundTheEdge() {
        for (int x : new int[]{17, 18, 0, 1, 2}) placeWithDefaultPie(x, 0, 1);
        assertNull(board.findWin(1));
    }

    @Test
    void sixOrMoreStillWinsAndReturnsTheWholeLine() {
        for (int x = 0; x < 6; x++) placeWithDefaultPie(x, 0, 1);
        Board.Win win = board.findWin(1);
        assertNotNull(win);
        assertEquals(6, win.stones().size());
    }

    @Test
    void thereAreThirteenDirections() {
        assertEquals(13, Board.DIRS.length);
    }

    @Test
    void clearResetsTheBoard() {
        for (int x = 0; x < 5; x++) placeWithDefaultPie(x, 0, 1);
        board.clear();
        assertNull(board.findWin(1));
        assertEquals(0, board.place(0, 0, 1));
    }
}