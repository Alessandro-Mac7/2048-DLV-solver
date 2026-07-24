package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AnswerSetParserTest {

    @Test
    void estrae_la_mossa_del_passo_zero() {
        String out = "{move(0,r), move(1,l), piene(7)}\nCOST 1@4 7@3\nOPTIMUM";
        assertEquals(Optional.of(Direction.RIGHT), AnswerSetParser.firstMove(out));
    }

    @Test
    void non_confonde_nomove_con_move() {
        String out = "{nomove(0,u), nomove(0,d), nomove(0,l), move(0,r)}";
        assertEquals(Optional.of(Direction.RIGHT), AnswerSetParser.firstMove(out));
    }

    @Test
    void ignora_i_passi_successivi_al_primo() {
        String out = "{move(2,u), move(0,d), move(1,l)}";
        assertEquals(Optional.of(Direction.DOWN), AnswerSetParser.firstMove(out));
    }

    @Test
    void output_vuoto_non_da_mossa() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove(""));
    }

    @Test
    void nessun_answer_set_non_da_mossa() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove("INCOHERENT"));
    }

    @Test
    void output_malformato_non_lancia_eccezioni() {
        assertEquals(Optional.empty(), AnswerSetParser.firstMove("move(x,y) move( move(0,"));
    }
}
