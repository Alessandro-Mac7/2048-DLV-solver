package it.mac7.dlv2048.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Board 4x4 immutabile. Le celle contengono ESPONENTI: 0 = vuota,
 * e > 0 = tessera di valore 1 &lt;&lt; e. Gli esponenti tengono i numeri
 * piccoli e combaciano con la rappresentazione usata nel programma ASP.
 */
public final class Board {

    public static final int SIZE = 4;
    private static final int CELLS = SIZE * SIZE;

    private final int[] cells;

    private Board(int[] cells) {
        this.cells = cells;
    }

    public static Board of(int... exponents) {
        if (exponents.length != CELLS) {
            throw new IllegalArgumentException("attese " + CELLS + " celle, ricevute " + exponents.length);
        }
        return new Board(exponents.clone());
    }

    public static Board empty() {
        return new Board(new int[CELLS]);
    }

    public int exponentAt(int row, int col) {
        return cells[row * SIZE + col];
    }

    public int valueAt(int row, int col) {
        int e = exponentAt(row, col);
        return e == 0 ? 0 : 1 << e;
    }

    public Board withTile(int row, int col, int exponent) {
        int[] copy = cells.clone();
        copy[row * SIZE + col] = exponent;
        return new Board(copy);
    }

    public List<int[]> emptyCells() {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (exponentAt(r, c) == 0) out.add(new int[]{r, c});
            }
        }
        return out;
    }

    public int maxExponent() {
        int max = 0;
        for (int e : cells) max = Math.max(max, e);
        return max;
    }

    public boolean hasMoves() {
        for (Direction d : Direction.values()) {
            if (move(d).moved()) return true;
        }
        return false;
    }

    /**
     * Applica una mossa. La meccanica e' definita QUI e in nessun altro punto:
     * il programma ASP la replica e un property test verifica che coincidano.
     */
    public MoveResult move(Direction d) {
        int[] next = new int[CELLS];
        int gained = 0;

        for (int line = 0; line < SIZE; line++) {
            int[] input = new int[SIZE];
            for (int p = 0; p < SIZE; p++) {
                int[] rc = project(d, line, p);
                input[p] = exponentAt(rc[0], rc[1]);
            }

            int[] slid = new int[SIZE];
            int write = 0;
            int read = 0;
            // compattazione + merge greedy dall'estremo di arrivo
            int[] compact = new int[SIZE];
            int n = 0;
            for (int p = 0; p < SIZE; p++) {
                if (input[p] != 0) compact[n++] = input[p];
            }
            while (read < n) {
                if (read + 1 < n && compact[read] == compact[read + 1]) {
                    int merged = compact[read] + 1;
                    slid[write++] = merged;
                    gained += 1 << merged;
                    read += 2;               // la tessera fusa non si rifonde
                } else {
                    slid[write++] = compact[read];
                    read++;
                }
            }

            for (int p = 0; p < SIZE; p++) {
                int[] rc = project(d, line, p);
                next[rc[0] * SIZE + rc[1]] = slid[p];
            }
        }

        boolean moved = !Arrays.equals(cells, next);
        return new MoveResult(new Board(next), gained, moved);
    }

    /**
     * Mappa (linea, posizione) -> (riga, colonna) per la direzione data.
     * Posizione 0 e' sempre l'estremo verso cui le tessere scivolano.
     * Identica alla relazione lin/5 del programma ASP.
     */
    private static int[] project(Direction d, int line, int pos) {
        return switch (d) {
            case LEFT  -> new int[]{line, pos};
            case RIGHT -> new int[]{line, SIZE - 1 - pos};
            case UP    -> new int[]{pos, line};
            case DOWN  -> new int[]{SIZE - 1 - pos, line};
        };
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Board b && Arrays.equals(cells, b.cells);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int v = valueAt(r, c);
                sb.append(v == 0 ? "    ." : String.format("%5d", v));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
