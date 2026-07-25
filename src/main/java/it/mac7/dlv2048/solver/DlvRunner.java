package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Esegue DLV2 come processo esterno su un programma passato via file temporaneo. */
public final class DlvRunner {

    /** Esito grezzo: stato piu' stdout. */
    public record DlvResult(SolverStatus status, String stdout) {}

    private DlvRunner() {}

    public static DlvResult run(Path binary, String program, Duration timeout) {
        Path tmpIn = null;
        Path tmpOut = null;
        Process proc = null;
        try {
            tmpIn = Files.createTempFile("dlv2048-", ".asp");
            Files.writeString(tmpIn, program, StandardCharsets.UTF_8);
            tmpOut = Files.createTempFile("dlv2048-out-", ".txt");

            // L'output va rediretto su file, non letto dalla pipe dopo waitFor:
            // se DLV2 produce piu' output di quanto la pipe del SO possa
            // bufferizzare (~64KB), il processo si blocca in write() perche'
            // nessuno la sta drenando, e waitFor scade in un falso TIMEOUT.
            // Con l'output su file non esiste pipe da riempire: il deadlock e'
            // strutturalmente impossibile.
            //
            // --printonlyoptimum e' obbligatorio: senza, DLV2 enumera TUTTI gli
            // answer set ottimi simmetrici e i tempi crollano. -n=1 non basta.
            proc = new ProcessBuilder(
                    binary.toString(),
                    "--silent",
                    "--printonlyoptimum",
                    "--filter=move/2",
                    tmpIn.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(tmpOut.toFile())
                    .start();

            if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                return new DlvResult(SolverStatus.TIMEOUT, "");
            }
            String out = Files.readString(tmpOut, StandardCharsets.UTF_8);
            return new DlvResult(SolverStatus.OK, out);

        } catch (IOException e) {
            return new DlvResult(SolverStatus.ERRORE, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DlvResult(SolverStatus.ERRORE, "");
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
            if (tmpIn != null) {
                try { Files.deleteIfExists(tmpIn); } catch (IOException ignored) {}
            }
            if (tmpOut != null) {
                try { Files.deleteIfExists(tmpOut); } catch (IOException ignored) {}
            }
        }
    }
}
