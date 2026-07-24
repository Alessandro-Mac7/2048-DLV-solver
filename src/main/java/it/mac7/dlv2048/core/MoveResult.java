package it.mac7.dlv2048.core;

/** Esito di una mossa: board risultante, punti guadagnati, se qualcosa si e' mosso. */
public record MoveResult(Board board, int gainedScore, boolean moved) {}
