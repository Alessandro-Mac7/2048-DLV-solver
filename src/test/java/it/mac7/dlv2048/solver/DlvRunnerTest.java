package it.mac7.dlv2048.solver;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DlvRunnerTest {

    /**
     * Regressione: se DLV2 produce piu' output di quanto la pipe del SO possa
     * bufferizzare (~64KB), leggere stdout SOLO dopo waitFor() causa un deadlock
     * strutturale (il processo si blocca in write(), nessuno drena la pipe, Java
     * aspetta una terminazione che non arriva) e un falso TIMEOUT.
     *
     * Il programma genera 50000 atomi move/2 veri (superstite all'unico filtro
     * consentito, --filter=move/2), producendo circa 739KB di stdout: ben oltre
     * la soglia di 64KB della pipe di sistema su macOS.
     */
    @Test
    void run_non_va_in_falso_timeout_con_output_oltre_la_pipe_del_so() {
        Optional<Path> binary = DlvBinary.locate();
        assumeTrue(binary.isPresent(), "DLV2 non trovato (DLV2_HOME/./dlv2/PATH) - test saltato");

        String program = "n(1..50000).\nmove(X,r) :- n(X).\n";

        DlvRunner.DlvResult result = DlvRunner.run(binary.get(), program, Duration.ofSeconds(15));

        assertEquals(SolverStatus.OK, result.status(),
                "atteso OK ma ottenuto " + result.status()
                        + " - possibile deadlock su output grande (falso TIMEOUT)");
        assertTrue(result.stdout().length() > 65536,
                "il programma di prova deve produrre oltre 64KB di stdout per essere un test di "
                        + "regressione valido, ottenuti " + result.stdout().length() + " caratteri");
    }

    /**
     * Regressione: con --redirectErrorStream(true) il testo di errore di DLV2 finisce
     * nello stdout catturato. Senza controllare exitValue(), un programma non valido
     * dava OK con uno stdout privo di move/2: il chiamante lo leggeva come
     * "nessuna mossa possibile" mentre in realta' DLV2 era morto.
     *
     * Codici di uscita osservati su dlv2 2.1.2 (arm64):
     *   0   answer set trovato / OPTIMUM / INCOHERENT (terminazione normale)
     *   100 file di input non apribile
     *   110 opzione da riga di comando non riconosciuta
     *   255 errore di sintassi ("Aborting due to parser errors")
     */
    @Test
    void programma_asp_non_valido_da_errore_non_ok() {
        Optional<Path> binary = DlvBinary.locate();
        assumeTrue(binary.isPresent(), "DLV2 non trovato (DLV2_HOME/./dlv2/PATH) - test saltato");

        String program = "questo non e' (((un programma ASP valido %%%\n";

        DlvRunner.DlvResult result = DlvRunner.run(binary.get(), program, Duration.ofSeconds(15));

        assertEquals(SolverStatus.ERRORE, result.status(),
                "un programma non parsabile deve dare ERRORE, non OK con stdout senza mosse");
    }

    /**
     * L'assenza di answer set (INCOHERENT) e' una terminazione NORMALE di DLV2
     * (uscita 0) e non va confusa con un errore: il chiamante deve poterla
     * distinguere per riportare "nessuna mossa possibile".
     */
    @Test
    void programma_senza_answer_set_resta_ok() {
        Optional<Path> binary = DlvBinary.locate();
        assumeTrue(binary.isPresent(), "DLV2 non trovato (DLV2_HOME/./dlv2/PATH) - test saltato");

        DlvRunner.DlvResult result = DlvRunner.run(binary.get(), "a.\n:- a.\n", Duration.ofSeconds(15));

        assertEquals(SolverStatus.OK, result.status(),
                "INCOHERENT e' una terminazione legittima, non un errore");
        assertTrue(result.stdout().contains("INCOHERENT"),
                "atteso INCOHERENT nello stdout, ottenuto: " + result.stdout());
    }
}
