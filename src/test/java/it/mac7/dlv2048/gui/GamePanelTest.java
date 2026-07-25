package it.mac7.dlv2048.gui;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.Game;
import it.mac7.dlv2048.solver.Solver;
import it.mac7.dlv2048.solver.SolverOutcome;
import it.mac7.dlv2048.solver.SolverStatus;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GamePanelTest {

    private static final long ATTESA_SOLVER_MS = 700;

    /** Solver finto: lento come DLV, ma con una mossa sempre legale e nessun processo esterno. */
    private static final class SolverLento implements Solver {
        final AtomicInteger invocazioni = new AtomicInteger();

        @Override
        public SolverOutcome bestMove(Board board) {
            invocazioni.incrementAndGet();
            try {
                Thread.sleep(ATTESA_SOLVER_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            for (Direction d : Direction.values()) {
                if (board.move(d).moved()) {
                    return new SolverOutcome(Optional.of(d), SolverStatus.OK, ATTESA_SOLVER_MS, 3);
                }
            }
            return SolverOutcome.fallito(SolverStatus.NESSUNA_SOLUZIONE, ATTESA_SOLVER_MS);
        }
    }

    private static KeyEvent tastoS(GamePanel panel) {
        return new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_S, 'S');
    }

    /** Preme S sull'Event Dispatch Thread e restituisce quanto l'EDT e' rimasto occupato. */
    private static long premiSSullEdt(GamePanel panel)
            throws InterruptedException, InvocationTargetException {
        long t0 = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> panel.keyPressed(tastoS(panel)));
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static void attendiFineRicerca(GamePanel panel)
            throws InterruptedException, InvocationTargetException {
        long scadenza = System.currentTimeMillis() + 10_000;
        while (panel.solverInCorso() && System.currentTimeMillis() < scadenza) {
            Thread.sleep(10);
        }
        SwingUtilities.invokeAndWait(() -> { }); // svuota la coda dell'EDT
        assertFalse(panel.solverInCorso(), "la ricerca non si e' mai conclusa");
    }

    /**
     * Regressione: keyPressed chiamava game.suggerisci() direttamente sull'Event
     * Dispatch Thread, cioe' teneva l'EDT occupato per tutta la durata della
     * ricerca (secondi) e la finestra restava congelata, senza nemmeno ridisegnarsi.
     */
    @Test
    void il_tasto_s_non_tiene_occupato_l_event_dispatch_thread() throws Exception {
        SolverLento solver = new SolverLento();
        Game game = new Game(solver);
        game.inizia();
        GamePanel panel = new GamePanel(game);

        long occupazioneEdt = premiSSullEdt(panel);

        assertTrue(occupazioneEdt < ATTESA_SOLVER_MS / 2,
                "l'EDT e' rimasto occupato " + occupazioneEdt + " ms mentre il solver ne impiega "
                        + ATTESA_SOLVER_MS + ": la ricerca non e' uscita dall'EDT");
        attendiFineRicerca(panel);
    }

    @Test
    void mentre_calcola_l_interfaccia_segnala_che_il_solver_sta_pensando() throws Exception {
        Game game = new Game(new SolverLento());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        premiSSullEdt(panel);

        assertTrue(panel.solverInCorso(), "durante la ricerca va mostrato che DLV sta pensando");
        attendiFineRicerca(panel);
        assertFalse(panel.solverInCorso());
    }

    /**
     * Tenere premuto S o pestarlo ripetutamente non deve mettere in coda N
     * processi DLV concorrenti: finche' una ricerca e' in corso le altre
     * pressioni vanno ignorate.
     */
    @Test
    void pressioni_ripetute_non_lanciano_ricerche_contemporanee() throws Exception {
        SolverLento solver = new SolverLento();
        Game game = new Game(solver);
        game.inizia();
        GamePanel panel = new GamePanel(game);

        for (int i = 0; i < 5; i++) {
            premiSSullEdt(panel);
        }
        attendiFineRicerca(panel);

        assertEquals(1, solver.invocazioni.get(),
                "cinque pressioni di S hanno lanciato " + solver.invocazioni.get() + " ricerche");
    }

    @Test
    void a_fine_calcolo_la_mossa_e_applicata_e_l_esito_registrato() throws Exception {
        Game game = new Game(new SolverLento());
        game.inizia();
        GamePanel panel = new GamePanel(game);
        Board prima = game.board();

        premiSSullEdt(panel);
        assertEquals(prima, game.board(), "la mossa non va applicata prima che il calcolo finisca");

        attendiFineRicerca(panel);

        assertNotEquals(prima, game.board(), "a calcolo finito la mossa suggerita va applicata");
        assertNotNull(panel.ultimoEsitoSolver(), "l'esito va registrato per l'indicazione a schermo");
        assertEquals(SolverStatus.OK, panel.ultimoEsitoSolver().status());
    }

    /**
     * Regressione: l'indice del colore era log2(valore)+1 su una tabella di 12
     * elementi, quindi una tessera 2048 (log2 = 11) chiedeva l'indice 12 e
     * faceva saltare drawTile con ArrayIndexOutOfBounds. Oggi il caso e'
     * irraggiungibile solo per un accidente dell'ordine con cui Game aggiorna
     * lo stato dopo la vittoria: non e' una garanzia su cui appoggiarsi.
     */
    @Test
    void l_indice_di_colore_resta_dentro_la_tabella_per_ogni_tessera() {
        for (int esponente = 1; esponente <= 17; esponente++) {
            int valore = 1 << esponente;
            int i = GamePanel.indiceColore(valore);
            assertTrue(i >= 0 && i < GamePanel.COLOR_TABLE.length,
                    "tessera " + valore + ": indice " + i + " fuori da una tabella di "
                            + GamePanel.COLOR_TABLE.length + " colori");
        }
    }

    @Test
    void la_tessera_2048_usa_l_ultimo_colore_della_tabella() {
        assertEquals(GamePanel.COLOR_TABLE.length - 1, GamePanel.indiceColore(2048));
    }

    @Test
    void sotto_2048_l_indice_resta_quello_di_sempre() {
        assertEquals(2, GamePanel.indiceColore(2));
        assertEquals(3, GamePanel.indiceColore(4));
        assertEquals(8, GamePanel.indiceColore(128));
        assertEquals(11, GamePanel.indiceColore(1024));
    }
}
