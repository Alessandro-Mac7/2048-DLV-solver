package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DlvBinaryTest {

    @Test
    void il_checksum_atteso_e_quello_della_release_2_1_2() {
        assertEquals(64, DlvBinary.EXPECTED_SHA256.length());
        assertEquals("b169b75dd7ee780b14ebf03158804ec010a71f27e532a3c9204b7ab01c3c92d7",
                DlvBinary.EXPECTED_SHA256);
    }

    @Test
    void calcola_lo_sha256_di_un_file(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("vuoto.bin");
        Files.write(f, new byte[0]);
        // SHA-256 della stringa vuota
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                DlvBinary.sha256(f));
    }

    @Test
    void locate_non_lancia_quando_il_binario_manca() {
        assertDoesNotThrow(DlvBinary::locate);
    }

    @Test
    void i_candidati_del_path_saltano_i_segmenti_vuoti_e_sono_assoluti() {
        // Stesso caso usato dal revisore per dimostrare il difetto: un PATH con
        // un segmento vuoto in mezzo ("/usr/bin::/bin") non deve produrre un
        // candidato "dlv2" relativo (che duplicherebbe il controllo ./dlv2 gia'
        // fatto altrove) e ogni candidato restituito deve essere assoluto.
        List<Path> candidati = DlvBinary.candidatiInPath("/usr/bin::/bin");

        assertEquals(2, candidati.size(), "il segmento vuoto non deve produrre un candidato");
        for (Path p : candidati) {
            assertTrue(p.isAbsolute(), "ogni candidato deve essere assoluto: " + p);
        }
        assertEquals(Path.of("/usr/bin/dlv2"), candidati.get(0));
        assertEquals(Path.of("/bin/dlv2"), candidati.get(1));
    }

    @Test
    void i_candidati_del_path_sono_vuoti_quando_path_e_null() {
        assertTrue(DlvBinary.candidatiInPath(null).isEmpty());
    }
}
