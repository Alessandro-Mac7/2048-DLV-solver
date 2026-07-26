package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AspEncoderTest {

    @Test
    void emette_un_fatto_per_ogni_cella_occupata() {
        Board b = Board.of(1,0,0,0, 0,2,0,0, 0,0,0,0, 0,0,0,3);
        String f = AspEncoder.facts(b, 6);
        assertTrue(f.contains("at(0,0,0,1)."));
        assertTrue(f.contains("at(0,1,1,2)."));
        assertTrue(f.contains("at(0,3,3,3)."));
    }

    @Test
    void non_emette_fatti_per_le_celle_vuote() {
        Board b = Board.of(1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        String f = AspEncoder.facts(b, 6);
        assertFalse(f.contains(",0)."), "le celle vuote non vanno emesse");
    }

    @Test
    void emette_orizzonte_tempo_e_passi_coerenti() {
        String f = AspEncoder.facts(Board.empty(), 6);
        assertTrue(f.contains("horizon(6)."));
        assertTrue(f.contains("time(0..6)."));
        assertTrue(f.contains("step(0..5)."));
    }

    @Test
    void orizzonte_non_positivo_e_rifiutato() {
        assertThrows(IllegalArgumentException.class,
                () -> AspEncoder.facts(Board.empty(), 0));
    }

    @Test
    void avversario_aggiunge_una_foglia_per_casella_dopo_la_catena() {
        String f = AspEncoder.factsAvversario(Board.of(1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 2);
        // la catena resta quella del piano lineare
        assertTrue(f.contains("horizon(2)."));
        assertTrue(f.contains("step(0..1)."));
        assertTrue(f.contains("at(0,0,0,1)."));
        // le foglie stanno dopo la catena: nessuna collisione con i suoi passi
        assertTrue(f.contains("ramo(0,3)."));
        assertTrue(f.contains("ramo(15,18)."));
        assertTrue(f.contains("time(3..18)."), "intervallo delle foglie errato:\n" + f);
    }

    @Test
    void avversario_con_orizzonte_non_positivo_e_rifiutato() {
        assertThrows(IllegalArgumentException.class,
                () -> AspEncoder.factsAvversario(Board.empty(), 0));
    }
}
