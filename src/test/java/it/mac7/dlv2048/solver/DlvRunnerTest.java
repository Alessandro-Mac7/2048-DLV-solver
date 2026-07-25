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
}
