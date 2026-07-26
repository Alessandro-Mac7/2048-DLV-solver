package it.mac7.dlv2048.core;

import java.util.List;

/**
 * Esito di una mossa: board risultante, punti guadagnati, se qualcosa si e'
 * mosso, e i movimenti di ogni tessera (per l'animazione in grafica).
 */
public record MoveResult(Board board, int gainedScore, boolean moved, List<TileMove> movimenti) {}
