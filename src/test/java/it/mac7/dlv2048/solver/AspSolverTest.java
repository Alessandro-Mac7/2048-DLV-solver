package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AspSolverTest {

    private static boolean dlvDisponibile() {
        return DlvBinary.locate().isPresent();
    }

    @Test
    void senza_binario_riporta_stato_esplicito_e_nessuna_mossa() {
        AspSolver s = new AspSolver(2, Duration.ofSeconds(3), java.util.Optional.empty());
        SolverOutcome o = s.bestMove(Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0));
        assertTrue(o.move().isEmpty());
        assertEquals(SolverStatus.BINARIO_ASSENTE, o.status());
    }

    @Test
    void trova_la_mossa_su_una_board_con_un_solo_merge_possibile() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // solo la riga 0 ha due tessere uguali affiancate
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        SolverOutcome o = new AspSolver(1, Duration.ofSeconds(5)).bestMove(b);
        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.move().isPresent());
    }

    @Test
    void su_board_bloccata_non_propone_mosse() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // scacchiera alternata: nessuna mossa legale
        Board b = Board.of(1,2,1,2, 2,1,2,1, 1,2,1,2, 2,1,2,1);
        SolverOutcome o = new AspSolver(1, Duration.ofSeconds(5)).bestMove(b);
        assertTrue(o.move().isEmpty());
    }

    @Test
    void rispetta_il_budget_a_orizzonte_sei() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Board b = Board.of(1,1,2,3, 2,2,3,4, 3,3,4,5, 0,0,0,1);
        SolverOutcome o = new AspSolver(6, Duration.ofSeconds(10)).bestMove(b);
        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.millis() < 3000, "H=6 fuori budget: " + o.millis() + " ms");
    }
}
