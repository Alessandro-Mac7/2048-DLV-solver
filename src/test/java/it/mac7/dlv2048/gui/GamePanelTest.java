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

    /** Come SolverLento, ma senza restituire mai una mossa: isola la verifica sull'esito. */
    private static final class SolverLentoSenzaMossa implements Solver {
        @Override
        public SolverOutcome bestMove(Board board) {
            try {
                Thread.sleep(ATTESA_SOLVER_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return SolverOutcome.fallito(SolverStatus.NESSUNA_SOLUZIONE, ATTESA_SOLVER_MS);
        }
    }

    /** Prima direzione che cambia davvero la board: con solo 2 tessere ce n'e' sempre una. */
    private static Direction mossaLegaleQualsiasi(Board board) {
        for (Direction d : Direction.values()) {
            if (board.move(d).moved()) return d;
        }
        throw new AssertionError("nessuna mossa legale sulla board: " + board);
    }

    private static KeyEvent tastoS(GamePanel panel) {
        return new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                KeyEvent.VK_S, 'S');
    }

    private static KeyEvent tastoFreccia(GamePanel panel, int codiceTasto) {
        return new KeyEvent(panel, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                codiceTasto, KeyEvent.CHAR_UNDEFINED);
    }

    private static int codiceTasto(Direction d) {
        return switch (d) {
            case UP -> KeyEvent.VK_UP;
            case DOWN -> KeyEvent.VK_DOWN;
            case LEFT -> KeyEvent.VK_LEFT;
            case RIGHT -> KeyEvent.VK_RIGHT;
        };
    }

    /** Preme S sull'Event Dispatch Thread e restituisce quanto l'EDT e' rimasto occupato. */
    private static long premiSSullEdt(GamePanel panel)
            throws InterruptedException, InvocationTargetException {
        long t0 = System.nanoTime();
        SwingUtilities.invokeAndWait(() -> panel.premi("S"));
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
     * Regressione: la guardia in done() controllava solo lo stato RUNNING, non
     * la board. Se l'utente muove con le frecce mentre DLV sta ancora
     * pensando, il suggerimento calcolato sulla board vecchia non deve
     * atterrare su quella nuova ne' essere mostrato come se la riguardasse.
     */
    @Test
    void una_mossa_dell_utente_durante_il_calcolo_scarta_l_esito_stale() throws Exception {
        Game game = new Game(new SolverLentoSenzaMossa());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        premiSSullEdt(panel);

        Board prima = game.board();
        Direction mossaUtente = mossaLegaleQualsiasi(prima);
        SwingUtilities.invokeAndWait(() -> game.muovi(mossaUtente));
        assertNotEquals(prima, game.board(), "precondizione: la mossa dell'utente deve cambiare la board");

        attendiFineRicerca(panel);

        assertNull(panel.ultimoEsitoSolver(),
                "l'esito calcolato sulla board precedente va scartato, non mostrato per quella nuova");
    }

    /**
     * Regressione: il KeyListener era registrato sulla finestra mentre il fuoco
     * stava sul pannello, quindi nell'applicazione vera i tasti non facevano
     * nulla — e nessun test se ne accorgeva, perche' chiamavano il gestore
     * direttamente scavalcando il collegamento. Qui si passa dai key binding.
     */
    @Test
    void le_frecce_muovono_davvero_passando_dai_tasti_legati() throws Exception {
        Game game = new Game(new SolverLentoSenzaMossa());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        Board prima = game.board();
        Direction mossa = mossaLegaleQualsiasi(prima);
        SwingUtilities.invokeAndWait(() -> panel.premi(mossa.name()));

        assertNotEquals(prima, game.board(),
                "il tasto " + mossa + " deve muovere: se non muove il collegamento e' rotto");
    }

    @Test
    void ogni_tasto_del_gioco_e_effettivamente_legato() throws Exception {
        GamePanel panel = new GamePanel(new Game(new SolverLentoSenzaMossa()));
        for (String t : new String[]{"UP", "DOWN", "LEFT", "RIGHT", "S", "SPACE"}) {
            SwingUtilities.invokeAndWait(() ->
                    assertDoesNotThrow(() -> panel.premi(t), "tasto non legato: " + t));
        }
    }

    /**
     * Variante della stessa guardia per un percorso diverso: non l'utente che
     * muove, ma una partita nuova avviata col mouse mentre DLV sta ancora
     * pensando. La mossa vecchia non deve atterrare sulla partita nuova.
     */
    @Test
    void una_partita_riavviata_durante_il_calcolo_scarta_l_esito() throws Exception {
        Game game = new Game(new SolverLentoSenzaMossa());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        premiSSullEdt(panel);

        Board prima = game.board();
        SwingUtilities.invokeAndWait(game::inizia);
        assertNotEquals(prima, game.board(), "precondizione: il riavvio deve cambiare la board");

        attendiFineRicerca(panel);

        assertNull(panel.ultimoEsitoSolver(),
                "l'esito della partita precedente non va applicato a quella nuova");
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

    /**
     * Regressione: il pannello girava un thread perenne in busy-loop
     * (while(goal){...} senza attesa), che a partita ferma consumava una CPU
     * intera. Sostituito da un timer di animazione che si avvia solo su una
     * mossa reale e si ferma da solo: se tornasse a girare all'infinito
     * questo test non finirebbe mai entro la scadenza.
     */
    @Test
    void subito_dopo_la_creazione_non_c_e_animazione_in_corso() {
        Game game = new Game(new SolverLento());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        assertFalse(panel.animazioneInCorso(), "a partita appena iniziata non deve girare nulla");
    }

    @Test
    void una_mossa_valida_avvia_l_animazione_e_si_ferma_da_sola() throws Exception {
        Game game = new Game(new SolverLento());
        game.inizia();
        GamePanel panel = new GamePanel(game);
        Direction mossa = mossaLegaleQualsiasi(game.board());

        SwingUtilities.invokeAndWait(() -> panel.premi(mossa.name()));
        assertTrue(panel.animazioneInCorso(), "una mossa che sposta tessere deve avviare l'animazione");

        long scadenza = System.currentTimeMillis() + 2000;
        while (panel.animazioneInCorso() && System.currentTimeMillis() < scadenza) {
            Thread.sleep(10);
        }
        assertFalse(panel.animazioneInCorso(), "l'animazione deve fermarsi da sola, non girare all'infinito");
    }

    /**
     * Regressione: le coordinate della griglia erano fisse (215 + c*121 ecc.)
     * su un pannello 900x700 e non seguivano il ridimensionamento.
     */
    @Test
    void il_layout_della_griglia_segue_le_dimensioni_del_pannello() {
        Game game = new Game(new SolverLento());
        game.inizia();
        GamePanel panel = new GamePanel(game);

        panel.setSize(900, 700);
        GamePanel.Layout piccolo = panel.geometria();

        panel.setSize(1800, 1400);
        GamePanel.Layout grande = panel.geometria();

        assertTrue(grande.gridSize() > piccolo.gridSize() * 1.5,
                "raddoppiando il pannello la griglia deve crescere, non restare fissa: "
                        + piccolo.gridSize() + " -> " + grande.gridSize());
        assertTrue(grande.cellSize() > piccolo.cellSize(),
                "le celle devono scalare con il pannello");
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
