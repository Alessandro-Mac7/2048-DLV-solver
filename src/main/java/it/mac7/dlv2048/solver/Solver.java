package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;

public interface Solver {
    SolverOutcome bestMove(Board board);
}
