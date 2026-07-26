package it.mac7.dlv2048.core;

import it.mac7.dlv2048.solver.AspSolver;
import it.mac7.dlv2048.solver.Solver;
import it.mac7.dlv2048.solver.SolverOutcome;
import java.util.List;
import java.util.Random;

/** Stato di una partita. Nessun campo statico: due partite sono indipendenti. */
public final class Game {

    public static final int ESPONENTE_VITTORIA = 11; // 2^11 = 2048

    private final Random rand = new Random();
    private final Solver solver;

    /** Letta anche dal worker del solver mentre l'EDT la scrive: volatile per pubblicarne le scritture. */
    private volatile Board board = Board.empty();
    private GameState stato = GameState.START;
    private int punteggio;

    public Game() {
        this(new AspSolver());
    }

    public Game(Solver solver) {
        this.solver = solver;
    }

    public void inizia() {
        board = Board.empty();
        punteggio = 0;
        stato = GameState.RUNNING;
        aggiungiTesseraCasuale();
        aggiungiTesseraCasuale();
    }

    public boolean muovi(Direction d) {
        if (stato != GameState.RUNNING) return false;
        MoveResult r = board.move(d);
        if (!r.moved()) return false;

        board = r.board();
        punteggio += r.gainedScore();

        if (board.maxExponent() >= ESPONENTE_VITTORIA) {
            stato = GameState.WON;
            return true;
        }
        aggiungiTesseraCasuale();
        if (!board.hasMoves()) stato = GameState.OVER;
        return true;
    }

    /** Chiede a DLV la mossa migliore. Non muove: decide il chiamante. */
    public SolverOutcome suggerisci() {
        return solver.bestMove(board);
    }

    private void aggiungiTesseraCasuale() {
        List<int[]> vuote = board.emptyCells();
        if (vuote.isEmpty()) return;
        int[] cella = vuote.get(rand.nextInt(vuote.size()));
        int esponente = rand.nextInt(10) == 0 ? 2 : 1; // 4 col 10%, altrimenti 2
        board = board.withTile(cella[0], cella[1], esponente);
    }

    public Board board() { return board; }
    public GameState stato() { return stato; }
    public int punteggio() { return punteggio; }
}
