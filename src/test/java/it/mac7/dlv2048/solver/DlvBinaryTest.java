package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
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
}
