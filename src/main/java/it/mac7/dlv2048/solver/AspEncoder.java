package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;

/** Traduce una board nei fatti di istanza attesi da /asp/plan.dlv2. */
public final class AspEncoder {

    private AspEncoder() {}

    public static String facts(Board board, int horizon) {
        if (horizon < 1) {
            throw new IllegalArgumentException("orizzonte deve essere >= 1, ricevuto " + horizon);
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("horizon(").append(horizon).append(").\n");
        sb.append("time(0..").append(horizon).append(").\n");
        sb.append("step(0..").append(horizon - 1).append(").\n");
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int e = board.exponentAt(r, c);
                if (e != 0) {
                    sb.append("at(0,").append(r).append(',').append(c)
                      .append(',').append(e).append(").\n");
                }
            }
        }
        return sb.toString();
    }
}
