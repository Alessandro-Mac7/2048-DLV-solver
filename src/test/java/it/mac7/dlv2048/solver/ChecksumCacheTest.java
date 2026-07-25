package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Il binario DLV pesa 3 MB: ricalcolarne lo SHA-256 a ogni suggerimento brucia
 * parte di un budget gia' stretto. La cache memorizza l'esito e lo invalida
 * quando cambia il percorso o il timestamp di modifica.
 */
class ChecksumCacheTest {

    /** Impronta finta che conta le invocazioni e restituisce il valore atteso. */
    private static final class ImprontaContata implements ChecksumCache.Impronta {
        final AtomicInteger calcoli = new AtomicInteger();
        private String risposta = DlvBinary.EXPECTED_SHA256;

        @Override
        public String sha256(Path file) {
            calcoli.incrementAndGet();
            return risposta;
        }

        void rispondiCon(String s) { risposta = s; }
    }

    private static Path fileFinto() throws IOException {
        Path p = Files.createTempFile("checksum-cache-", ".bin");
        Files.writeString(p, "contenuto", StandardCharsets.UTF_8);
        p.toFile().deleteOnExit();
        return p;
    }

    @Test
    void chiamate_ripetute_sullo_stesso_file_calcolano_una_volta_sola() throws IOException {
        Path bin = fileFinto();
        ImprontaContata impronta = new ImprontaContata();
        ChecksumCache cache = new ChecksumCache(impronta);

        for (int i = 0; i < 20; i++) {
            assertTrue(cache.valido(bin), "atteso valido alla chiamata " + i);
        }

        assertEquals(1, impronta.calcoli.get(),
                "lo SHA-256 va calcolato una sola volta, non a ogni bestMove");
    }

    @Test
    void un_timestamp_di_modifica_diverso_forza_il_ricalcolo() throws IOException {
        Path bin = fileFinto();
        ImprontaContata impronta = new ImprontaContata();
        ChecksumCache cache = new ChecksumCache(impronta);

        assertTrue(cache.valido(bin));
        assertEquals(1, impronta.calcoli.get());

        // il binario viene sostituito: nuovo mtime, impronta diversa
        Files.setLastModifiedTime(bin, FileTime.fromMillis(Files.getLastModifiedTime(bin).toMillis() + 5000));
        impronta.rispondiCon("0".repeat(64));

        assertFalse(cache.valido(bin), "cambiato il file, la cache deve invalidarsi");
        assertEquals(2, impronta.calcoli.get());
    }

    @Test
    void un_percorso_diverso_forza_il_ricalcolo() throws IOException {
        Path primo = fileFinto();
        Path secondo = fileFinto();
        ImprontaContata impronta = new ImprontaContata();
        ChecksumCache cache = new ChecksumCache(impronta);

        assertTrue(cache.valido(primo));
        impronta.rispondiCon("0".repeat(64));
        assertFalse(cache.valido(secondo), "percorso diverso: la voce in cache non vale");
        assertEquals(2, impronta.calcoli.get());
    }

    @Test
    void anche_l_esito_negativo_viene_memorizzato() throws IOException {
        Path bin = fileFinto();
        ImprontaContata impronta = new ImprontaContata();
        impronta.rispondiCon("0".repeat(64));
        ChecksumCache cache = new ChecksumCache(impronta);

        assertFalse(cache.valido(bin));
        assertFalse(cache.valido(bin));
        assertEquals(1, impronta.calcoli.get(),
                "un binario gia' riconosciuto come non valido non va riesaminato");
    }

    @Test
    void file_inesistente_non_e_valido_e_non_avvelena_la_cache() throws IOException {
        Path assente = Path.of(System.getProperty("java.io.tmpdir"), "dlv2-non-esiste-" + System.nanoTime());
        ImprontaContata impronta = new ImprontaContata();
        ChecksumCache cache = new ChecksumCache(impronta);

        assertFalse(cache.valido(assente));

        Path buono = fileFinto();
        assertTrue(cache.valido(buono));
    }
}
