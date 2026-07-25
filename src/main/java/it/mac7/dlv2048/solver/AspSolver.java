package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/** Solver che delega la scelta della mossa a DLV2 su un problema di pianificazione. */
public final class AspSolver implements Solver {

    private static final String RISORSA = "/asp/plan.dlv2";

    private final int horizon;
    private final Duration timeout;
    private final Optional<Path> binary;
    private final String programma;

    public AspSolver() {
        this(6, Duration.ofSeconds(3));
    }

    public AspSolver(int horizon, Duration timeout) {
        this(horizon, timeout, DlvBinary.locate());
    }

    public AspSolver(int horizon, Duration timeout, Optional<Path> binary) {
        this.horizon = horizon;
        this.timeout = timeout;
        this.binary = binary;
        this.programma = caricaProgramma();
    }

    private static String caricaProgramma() {
        try (InputStream in = AspSolver.class.getResourceAsStream(RISORSA)) {
            if (in == null) throw new IllegalStateException("risorsa mancante: " + RISORSA);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere " + RISORSA, e);
        }
    }

    @Override
    public SolverOutcome bestMove(Board board) {
        long t0 = System.nanoTime();
        if (binary.isEmpty()) {
            return SolverOutcome.fallito(SolverStatus.BINARIO_ASSENTE, elapsed(t0));
        }
        Path bin = binary.get();
        if (!DlvBinary.checksumValido(bin)) {
            return SolverOutcome.fallito(SolverStatus.CHECKSUM_ERRATO, elapsed(t0));
        }

        String programmaCompleto = programma + "\n" + AspEncoder.facts(board, horizon);
        DlvRunner.DlvResult res = DlvRunner.run(bin, programmaCompleto, timeout);
        if (res.status() != SolverStatus.OK) {
            return SolverOutcome.fallito(res.status(), elapsed(t0));
        }

        return AnswerSetParser.firstMove(res.stdout())
                .map(d -> new SolverOutcome(Optional.of(d), SolverStatus.OK, elapsed(t0)))
                .orElseGet(() -> SolverOutcome.fallito(SolverStatus.NESSUNA_SOLUZIONE, elapsed(t0)));
    }

    private static long elapsed(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}
