package omok3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure game logic (no UI): a 19x19 floor with up to 19 layers of stones.
 * Coordinates are [x][y][z]: x = left/right, y = height (0 is the bottom layer), z = depth.
 * Cell values: 0 = empty, 1 = black (first player's stone), 2 = white (second player's stone).
 *
 * Rules: stones fall to the lowest empty layer of a column (gravity), and five or more
 * stones of one color connected in a straight line in 3D win (13 directions).
 * Includes Pie Rule (Swap Rule): after the first stone (black) is placed, the second player
 * may choose to swap colors (taking control of black) or keep white.
 */
public class Board {
    public static final int N = 19;
    public static final int WIN = 5;

    private final int[][][] grid = new int[N][N][N];
    private int moveCount = 0;
    private int currentTurn = 1; // 1 = black, 2 = white
    private boolean pieChoicePending = false;
    private boolean pieSwapped = false;

    /** The 13 line directions (opposite directions describe the same line, so only half are kept). */
    static final int[][] DIRS;
    static {
        List<int[]> list = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 1; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int first = dx != 0 ? dx : (dy != 0 ? dy : dz);
                    if (first > 0) list.add(new int[]{dx, dy, dz});
                }
        DIRS = list.toArray(new int[0][]);
    }

    /** A winning line: all connected stones {x, y, z} (5 or more) and the line direction. */
    public record Win(List<int[]> stones, int[] dir) {}

    public int get(int x, int y, int z) {
        return grid[x][y][z];
    }

    public int getMoveCount() {
        return moveCount;
    }

    public int getCurrentTurn() {
        return currentTurn;
    }

    /** Returns true if the game is currently awaiting the second player's Pie Rule decision (after move 1). */
    public boolean isPieChoicePending() {
        return pieChoicePending;
    }

    /** Returns true if the second player chose to swap sides under the Pie Rule. */
    public boolean isPieSwapped() {
        return pieSwapped;
    }

    /**
     * Resolves the Pie Rule decision right after the first stone is placed.
     * @param swap true if the second player takes Black (swapping sides), false to keep White.
     * @throws IllegalStateException if Pie Rule decision is not currently pending.
     */
    public void decidePie(boolean swap) {
        if (!pieChoicePending) {
            throw new IllegalStateException("Pie rule decision is only available immediately after the first move.");
        }
        pieSwapped = swap;
        pieChoicePending = false;
        // Regardless of swap, the next stone placed on the board must be White (2).
        // If swapped, Player 2 now controls Black (so Player 1 now controls White and places stone 2).
        // If not swapped, Player 2 keeps White and places stone 2.
        currentTurn = 2;
    }

    /** Layer a new stone would land on in column (x, z), or -1 if the column is full or out of range. */
    public int dropY(int x, int z) {
        if (x < 0 || z < 0 || x >= N || z >= N) return -1;
        for (int y = 0; y < N; y++) if (grid[x][y][z] == 0) return y;
        return -1;
    }

    /** Drops a stone of the current turn's color into column (x, z). */
    public int place(int x, int z) {
        return place(x, z, currentTurn);
    }

    /** Drops a stone into column (x, z). Returns the layer it landed on, or -1 if not possible. */
    public int place(int x, int z, int player) {
        if (pieChoicePending) return -1; // Must resolve Pie Rule before placing the second stone
        int y = dropY(x, z);
        if (y < 0) return -1;
        grid[x][y][z] = player;
        moveCount++;
        if (moveCount == 1) {
            pieChoicePending = true;
        }
        currentTurn = (player == 1) ? 2 : 1;
        return y;
    }

    /** Removes the top stone of column (x, z) (used by undo). Returns its layer, or -1 if the column is empty. */
    public int removeTop(int x, int z) {
        int y = dropY(x, z);
        y = (y < 0 ? N : y) - 1;
        if (y < 0 || x < 0 || z < 0 || x >= N || z >= N) return -1;
        int removedPlayer = grid[x][y][z];
        grid[x][y][z] = 0;
        if (moveCount > 0) {
            moveCount--;
        }
        if (moveCount == 0) {
            pieChoicePending = false;
            pieSwapped = false;
            currentTurn = 1;
        } else if (moveCount == 1) {
            pieChoicePending = true;
            pieSwapped = false;
            currentTurn = 2;
        } else {
            currentTurn = removedPlayer;
        }
        return y;
    }

    public void clear() {
        for (int[][] a : grid) for (int[] b : a) java.util.Arrays.fill(b, 0);
        moveCount = 0;
        currentTurn = 1;
        pieChoicePending = false;
        pieSwapped = false;
    }

    /** Returns the winning line of the player if five or more stones are connected, otherwise null. */
    public Win findWin(int player) {
        for (int x = 0; x < N; x++)
            for (int y = 0; y < N; y++)
                for (int z = 0; z < N; z++) {
                    if (grid[x][y][z] != player) continue;
                    for (int[] d : DIRS) {
                        // Only count from the start of a run
                        if (is(player, x - d[0], y - d[1], z - d[2])) continue;
                        int len = 0;
                        while (is(player, x + d[0] * len, y + d[1] * len, z + d[2] * len)) len++;
                        if (len >= WIN) {
                            List<int[]> stones = new ArrayList<>();
                            for (int k = 0; k < len; k++)
                                stones.add(new int[]{x + d[0] * k, y + d[1] * k, z + d[2] * k});
                            return new Win(stones, d);
                        }
                    }
                }
        return null;
    }

    private boolean is(int player, int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < N && y < N && z < N && grid[x][y][z] == player;
    }
}