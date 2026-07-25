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
        Path tmp = null;
        Process proc = null;
        try {
            tmp = Files.createTempFile("dlv2048-", ".asp");
            Files.writeString(tmp, program, StandardCharsets.UTF_8);

            // --printonlyoptimum e' obbligatorio: senza, DLV2 enumera TUTTI gli
            // answer set ottimi simmetrici e i tempi crollano. -n=1 non basta.
            proc = new ProcessBuilder(
                    binary.toString(),
                    "--silent",
                    "--printonlyoptimum",
                    "--filter=move/2",
                    tmp.toString())
                    .redirectErrorStream(true)
                    .start();

            if (!proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                proc.destroyForcibly();
                return new DlvResult(SolverStatus.TIMEOUT, "");
            }
            String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new DlvResult(SolverStatus.OK, out);

        } catch (IOException e) {
            return new DlvResult(SolverStatus.ERRORE, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new DlvResult(SolverStatus.ERRORE, "");
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        }
    }
}
