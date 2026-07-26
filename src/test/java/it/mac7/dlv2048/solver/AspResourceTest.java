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
     * La sovrapposizione avversariale e' additiva e va spenta per default: se
     * dichiarasse spawnEsplicito da sola non basterebbe, deve essere il
     * programma base a non contenerlo mai come fatto.
     */
    @Test
    void la_sovrapposizione_avversariale_e_separata_e_non_attiva_da_sola() throws Exception {
        String base = leggi("/asp/plan.dlv2");
        String avv = leggi("/asp/adversary.dlv2");
        assertFalse(base.contains("\nspawnEsplicito."), "il programma base non deve attivarsi da solo");
        assertTrue(avv.contains("\nspawnEsplicito."), "la sovrapposizione non si attiva");
        assertTrue(base.contains("not spawnEsplicito"), "il base non cede il campo alla sovrapposizione");
    }

    private String leggi(String risorsa) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(risorsa)) {
            assertNotNull(in, "risorsa " + risorsa + " assente");
            return new String(in.readAllBytes());
        }
    }
}
