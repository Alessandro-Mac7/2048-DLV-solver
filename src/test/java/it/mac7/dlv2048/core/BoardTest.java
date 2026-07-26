package it.mac7.dlv2048.core;

import org.junit.jupiter.api.Test;
import java.util.List;
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

    /**
     * I movimenti servono solo alla grafica (animazione di scivolamento): la
     * board risultante e' gia' coperta dagli altri test, qui si verifica solo
     * la tracciatura da dove a dove si sposta ogni tessera.
     */
    @Test
    void uno_scorrimento_senza_merge_riporta_partenza_e_arrivo_di_ogni_tessera() {
        // . . 2 . -> 2 . . .  (colonna 2, riga 0, scivola in colonna 0)
        Board b = board(0,0,1,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertEquals(1, r.movimenti().size());
        TileMove m = r.movimenti().get(0);
        assertEquals(0, m.fromRow());
        assertEquals(2, m.fromCol());
        assertEquals(0, m.toRow());
        assertEquals(0, m.toCol());
        assertFalse(m.fuso());
        assertEquals(1, m.esponenteIniziale());
        assertEquals(1, m.esponenteFinale());
    }

    @Test
    void un_merge_produce_due_movimenti_sulla_stessa_destinazione() {
        // 2 2 . . -> 4 . . .
        Board b = board(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertEquals(2, r.movimenti().size());
        for (TileMove m : r.movimenti()) {
            assertTrue(m.fuso());
            assertEquals(0, m.toRow());
            assertEquals(0, m.toCol());
            assertEquals(1, m.esponenteIniziale());
            assertEquals(2, m.esponenteFinale());
        }
        List<Integer> partenze = r.movimenti().stream().map(TileMove::fromCol).sorted().toList();
        assertEquals(List.of(0, 1), partenze);
    }

    @Test
    void una_tessera_ferma_ha_partenza_e_arrivo_coincidenti() {
        Board b = board(1,2,3,4, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        MoveResult r = b.move(Direction.LEFT);
        assertEquals(4, r.movimenti().size());
        for (TileMove m : r.movimenti()) {
            assertEquals(m.fromRow(), m.toRow());
            assertEquals(m.fromCol(), m.toCol());
            assertFalse(m.fuso());
        }
    }

    private static int[] riga(Board b, int r) {
        return new int[]{b.exponentAt(r,0), b.exponentAt(r,1),
                         b.exponentAt(r,2), b.exponentAt(r,3)};
    }
}
