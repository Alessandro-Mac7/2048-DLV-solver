package it.mac7.dlv2048.solver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Esegue DLV2 come processo esterno su un programma passato via file temporaneo. */
public final class DlvRunner {

    /** Unico codice di uscita che DLV2 usa per una terminazione normale. */
    private static final int NORMALE = 0;

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

            // Il codice di uscita e' l'UNICO segnale affidabile di errore: con
            // redirectErrorStream(true) i messaggi diagnostici finiscono nello
            // stesso flusso dell'answer set, e uno stdout senza move/2 e'
            // indistinguibile fra "nessuna soluzione" e "DLV e' morto".
            //
            // Codici osservati su dlv2 2.1.2 (arm64):
            //     0 -> terminazione normale. Copre sia l'answer set trovato /
            //          OPTIMUM sia INCOHERENT: l'assenza di answer set e' un
            //          esito legittimo del solver, non un guasto.
            //   100 -> file di input non apribile
            //   110 -> opzione da riga di comando non riconosciuta
            //   255 -> errore di sintassi ("Aborting due to parser errors")
            int uscita = proc.exitValue();
            if (uscita != NORMALE) {
                return new DlvResult(SolverStatus.ERRORE, out);
            }
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
