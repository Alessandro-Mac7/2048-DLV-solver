package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;

/**
 * Esito di una richiesta al solver. {@code millis} e' la durata TOTALE della
 * ricerca, non dell'ultimo livello; {@code horizonRaggiunto} e' 0 se
 * l'approfondimento non ha prodotto alcun piano.
 */
public record SolverOutcome(Optional<Direction> move, SolverStatus status, long millis,
                            int horizonRaggiunto) {

    public static SolverOutcome fallito(SolverStatus status, long millis) {
        return new SolverOutcome(Optional.empty(), status, millis, 0);
    }
}
