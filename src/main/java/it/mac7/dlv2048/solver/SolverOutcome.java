package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;

/**
 * Esito di una richiesta al solver.
 *
 * @param move             la mossa scelta, se ce n'e' una
 * @param status           esito dell'interazione con DLV, mostrabile in UI
 * @param millis           durata TOTALE della ricerca, non dell'ultimo livello
 *                         di approfondimento
 * @param horizonRaggiunto profondita' dell'ultimo livello concluso con successo
 *                         dall'approfondimento iterativo; 0 se nessuno
 */
public record SolverOutcome(Optional<Direction> move, SolverStatus status, long millis,
                            int horizonRaggiunto) {

    public static SolverOutcome fallito(SolverStatus status, long millis) {
        return new SolverOutcome(Optional.empty(), status, millis, 0);
    }
}
