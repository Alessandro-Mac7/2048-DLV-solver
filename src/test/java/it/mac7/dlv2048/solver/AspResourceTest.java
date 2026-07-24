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
}
