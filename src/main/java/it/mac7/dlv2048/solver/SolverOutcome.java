package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;

/** Esito di una richiesta al solver: mossa (se c'e'), stato, tempo impiegato. */
public record SolverOutcome(Optional<Direction> move, SolverStatus status, long millis) {

    public static SolverOutcome fallito(SolverStatus status, long millis) {
        return new SolverOutcome(Optional.empty(), status, millis);
    }
}
