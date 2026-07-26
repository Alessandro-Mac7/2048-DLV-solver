package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;

/** Traduce una board nei fatti di istanza attesi da /asp/plan.dlv2. */
public final class AspEncoder {

    private AspEncoder() {}

    /**
     * Fatti per la modalita' avversariale: gli stessi del piano lineare, piu' i
     * sedici stati foglia che /asp/adversary.dlv2 usa per il piazzamento della
     * tessera dopo l'ultima mossa. Gli identificativi delle foglie stanno dopo
     * la catena, quindi non possono collidere con i suoi passi.
     *
     * <p>I rami sono sempre sedici, uno per casella. Quelli su casella occupata
     * non producono alcuno stato e spariscono in fase di grounding: quali
     * caselle siano libere dipende dal piano scelto e qui non e' noto.
     */
    public static String factsAvversario(Board board, int horizon) {
        StringBuilder sb = new StringBuilder(1024).append(facts(board, horizon));
        int celle = Board.SIZE * Board.SIZE;
        for (int k = 0; k < celle; k++) {
            sb.append("ramo(").append(k).append(',').append(horizon + 1 + k).append(").\n");
        }
        sb.append("time(").append(horizon + 1).append("..")
          .append(horizon + celle).append(").\n");
        return sb.toString();
    }

    public static String facts(Board board, int horizon) {
        if (horizon < 1) {
            throw new IllegalArgumentException("orizzonte deve essere >= 1, ricevuto " + horizon);
        }
        StringBuilder sb = new StringBuilder(512);
        sb.append("horizon(").append(horizon).append(").\n");
        sb.append("time(0..").append(horizon).append(").\n");
        sb.append("step(0..").append(horizon - 1).append(").\n");
        appendStato(sb, board, 0);
        return sb.toString();
    }

    private static void appendStato(StringBuilder sb, Board board, int nodo) {
        for (int r = 0; r < Board.SIZE; r++) {
            for (int c = 0; c < Board.SIZE; c++) {
                int e = board.exponentAt(r, c);
                if (e != 0) {
                    // il programma considera "occupata" ogni cella con un fatto at/4:
                    // una cella vuota va omessa, non codificata come at(...,0)
                    sb.append("at(").append(nodo).append(',').append(r).append(',').append(c)
                      .append(',').append(e).append(").\n");
                }
            }
        }
    }
}
