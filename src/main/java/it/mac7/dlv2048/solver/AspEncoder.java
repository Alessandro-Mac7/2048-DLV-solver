package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import java.util.ArrayList;
import java.util.List;

/** Traduce una board nei fatti di istanza attesi da /asp/plan.dlv2. */
public final class AspEncoder {

    private AspEncoder() {}

    /**
     * Fatti per la modalita' avversariale: lo stato iniziale piu' lo scheletro
     * dell'albero di gioco atteso da /asp/adversary.dlv2. A ogni mossa segue un
     * piazzamento dell'avversario, quindi le foglie sono gli stati DOPO la
     * tessera. Con {@code mosse} mosse i nodi sono O(16^mosse): e' il motivo per
     * cui questa modalita' vive su orizzonti corti.
     *
     * <p>I rami sono sempre 16, uno per casella. Quelli su casella occupata non
     * producono alcuno stato e il grounder li fa sparire: e' piu' semplice
     * generarli tutti che sapere qui quali sono liberi dopo la mossa, cosa che
     * dipende dalla mossa e quindi non e' nota al chiamante.
     */
    public static String factsAvversario(Board board, int mosse) {
        if (mosse < 1) {
            throw new IllegalArgumentException("mosse deve essere >= 1, ricevuto " + mosse);
        }
        StringBuilder sb = new StringBuilder(4096);
        sb.append("radice(0).\n");
        appendStato(sb, board, 0);

        int prossimo = 1;
        List<Integer> stati = new ArrayList<>(List.of(0));
        for (int d = 0; d < mosse; d++) {
            List<Integer> figli = new ArrayList<>();
            for (int n : stati) {
                int esito = prossimo++;
                sb.append("succ(").append(n).append(',').append(esito).append(").\n");
                for (int k = 0; k < Board.SIZE * Board.SIZE; k++) {
                    int figlio = prossimo++;
                    sb.append("ramo(").append(esito).append(',').append(k)
                      .append(',').append(figlio).append(").\n");
                    figli.add(figlio);
                }
            }
            stati = figli;
        }
        // gli identificativi sono assegnati in sequenza: un solo intervallo li copre
        sb.append("time(0..").append(prossimo - 1).append(").\n");
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
