package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.*;

class AspResourceTest {

    @Test
    void il_programma_asp_e_sul_classpath_ed_e_completo() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/asp/plan.dlv2")) {
            assertNotNull(in, "risorsa /asp/plan.dlv2 assente");
            String src = new String(in.readAllBytes());
            assertTrue(src.contains("move(T,D) | nomove(T,D)"), "manca il guess");
            assertTrue(src.contains(":~"), "mancano i weak constraint");
            assertFalse(src.contains("#maxint"), "#maxint e' sintassi DLV1");
            assertFalse(src.contains(" v nomove"), "disgiunzione in sintassi DLV1");
        }
    }

    /**
     * La sovrapposizione avversariale e' additiva: concatenarla al programma
     * base non deve cambiare nulla finche' il chiamante non fornisce i fatti
     * ramo/2. Il programma base non deve contenerne alcuno.
     */
    @Test
    void la_sovrapposizione_avversariale_e_separata_e_inerte_senza_i_suoi_fatti() throws Exception {
        String base = leggi("/asp/plan.dlv2");
        String avv = leggi("/asp/adversary.dlv2");
        assertFalse(base.contains("ramo("), "il programma base non deve conoscere i rami");
        assertTrue(avv.contains("ramo(K,N)"), "la sovrapposizione non legge i rami");
        assertTrue(avv.contains(":~"), "la sovrapposizione non aggiunge alcun obiettivo");
    }

    private String leggi(String risorsa) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(risorsa)) {
            assertNotNull(in, "risorsa " + risorsa + " assente");
            return new String(in.readAllBytes());
        }
    }
}
