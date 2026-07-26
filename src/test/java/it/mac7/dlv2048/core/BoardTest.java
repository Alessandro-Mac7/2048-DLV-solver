package it.mac7.dlv2048.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private static Board board(int... exponents) {
        return Board.of(exponents);
    }

    @Test
    void merge_semplice_a_sinistra() {
        // 2 2 4 8  ->  4 4 8 .
        Board b = board(1,1,2,3, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertTrue(r.moved());
        assertArrayEquals(new int[]{2,2,3,0}, riga(r.board(), 0));
        assertEquals(4, r.gainedScore());
    }

    @Test
    void quattro_uguali_danno_due_merge_non_uno() {
        // 2 2 2 2 -> 4 4  (NON 8)
        Board b = board(1,1,1,1, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertArrayEquals(new int[]{2,2,0,0}, riga(r.board(), 0));
        assertEquals(8, r.gainedScore()); // 4 + 4
    }

    @Test
    void una_tessera_non_si_fonde_due_volte() {
        // 4 2 2 . -> 4 4 . .
        Board b = board(2,1,1,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertArrayEquals(new int[]{2,2,0,0}, riga(r.board(), 0));
    }

    @Test
    void mossa_senza_effetto_non_e_una_mossa() {
        Board b = board(1,2,3,4, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        assertFalse(b.move(Direction.LEFT).moved());
    }

    @Test
    void destra_e_speculare_a_sinistra() {
        Board b = board(1,1,2,3, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        assertArrayEquals(new int[]{0,2,2,3}, riga(b.move(Direction.RIGHT).board(), 0));
    }

    @Test
    void la_board_di_partenza_non_viene_modificata() {
        Board b = board(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        b.move(Direction.LEFT);
        assertEquals(1, b.exponentAt(0, 0));
        assertEquals(1, b.exponentAt(0, 1));
    }

    private static int[] riga(Board b, int r) {
        return new int[]{b.exponentAt(r,0), b.exponentAt(r,1),
                         b.exponentAt(r,2), b.exponentAt(r,3)};
    }
}
