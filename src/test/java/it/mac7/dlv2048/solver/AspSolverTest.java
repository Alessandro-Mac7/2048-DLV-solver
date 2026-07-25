package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.Game;
import it.mac7.dlv2048.core.GameState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AspSolverTest {

    /**
     * Margine concesso oltre il budget: il budget governa il tempo dato a DLV,
     * ma attorno ci sono creazione dei file temporanei, fork del processo e
     * destroyForcibly, che non sono istantanei. Sotto il decimo di secondo.
     */
    private static final long TOLLERANZA_MS = 100;

    private static boolean dlvDisponibile() {
        return DlvBinary.locate().isPresent();
    }

    @Test
    void senza_binario_riporta_stato_esplicito_e_nessuna_mossa() {
        AspSolver s = new AspSolver(2, Duration.ofSeconds(3), Optional.empty());
        SolverOutcome o = s.bestMove(Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0));
        assertTrue(o.move().isEmpty());
        assertEquals(SolverStatus.BINARIO_ASSENTE, o.status());
        assertEquals(0, o.horizonRaggiunto());
    }

    @Test
    void trova_la_mossa_su_una_board_con_un_solo_merge_possibile() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // solo la riga 0 ha due tessere uguali affiancate
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        SolverOutcome o = new AspSolver(1, Duration.ofSeconds(5)).bestMove(b);
        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.move().isPresent());
        assertEquals(1, o.horizonRaggiunto());
    }

    /**
     * Su una board senza mosse legali l'esito corretto e' NESSUNA_SOLUZIONE:
     * asserire solo che la mossa manchi lascerebbe passare anche TIMEOUT ed
     * ERRORE, cioe' proprio i casi che l'utente deve poter distinguere.
     */
    @Test
    void su_board_bloccata_riporta_nessuna_soluzione() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        // scacchiera alternata: nessuna mossa legale
        Board b = Board.of(1,2,1,2, 2,1,2,1, 1,2,1,2, 2,1,2,1);
        assertFalse(b.hasMoves(), "la board di prova deve essere davvero bloccata");

        SolverOutcome o = new AspSolver(8, Duration.ofSeconds(5)).bestMove(b);

        assertEquals(SolverStatus.NESSUNA_SOLUZIONE, o.status());
        assertTrue(o.move().isEmpty());
        assertEquals(0, o.horizonRaggiunto());
    }

    /**
     * Sostituisce il vecchio "rispetta_il_budget_a_orizzonte_sei": con
     * l'approfondimento iterativo l'orizzonte non e' piu' un parametro fisso da
     * rispettare, il contratto e' il BUDGET TOTALE della ricerca. Un risultato
     * c'e' sempre, almeno a orizzonte 1.
     */
    @Test
    void rispetta_il_budget_totale_e_raggiunge_almeno_orizzonte_uno() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Board b = Board.of(1,1,2,3, 2,2,3,4, 3,3,4,5, 0,0,0,1);
        Duration budget = Duration.ofSeconds(3);

        SolverOutcome o = new AspSolver(8, budget).bestMove(b);

        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.move().isPresent());
        assertTrue(o.horizonRaggiunto() >= 1,
                "l'approfondimento deve chiudere almeno l'orizzonte 1, raggiunto "
                        + o.horizonRaggiunto());
        assertTrue(o.millis() <= budget.toMillis() + TOLLERANZA_MS,
                "budget totale sforato: " + o.millis() + " ms su " + budget.toMillis());
    }

    @Test
    void con_budget_ampio_approfondisce_oltre_il_primo_livello() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Board b = Board.of(1,1,0,0, 0,2,0,0, 0,0,0,0, 0,0,0,3);

        SolverOutcome o = new AspSolver(8, Duration.ofSeconds(5)).bestMove(b);

        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.horizonRaggiunto() >= 2,
                "su board facile e budget ampio deve superare l'orizzonte 1, fermo a "
                        + o.horizonRaggiunto());
    }

    @Test
    void non_supera_mai_l_orizzonte_massimo_configurato() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);

        SolverOutcome o = new AspSolver(3, Duration.ofSeconds(20)).bestMove(b);

        assertEquals(SolverStatus.OK, o.status());
        assertTrue(o.horizonRaggiunto() <= 3,
                "orizzonte massimo 3 sforato: " + o.horizonRaggiunto());
    }

    /**
     * Lo scenario che ha smascherato l'orizzonte fisso: venti pressioni di S di
     * seguito su una partita che avanza davvero. Con H=6 fisso il 45% andava in
     * timeout; l'approfondimento iterativo deve restituire sempre una mossa
     * quando una mossa legale esiste, e sempre entro il budget.
     */
    @Test
    void venti_suggerimenti_consecutivi_su_partita_reale() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");

        Duration budget = Duration.ofSeconds(2);
        Game game = new Game(new AspSolver(8, budget));
        game.inizia();

        int chiamate = 0;
        long peggiore = 0;
        long totale = 0;
        int orizzonteMinimo = Integer.MAX_VALUE;
        int orizzonteMassimo = 0;

        while (chiamate < 20) {
            if (game.stato() != GameState.RUNNING) {
                game.inizia(); // partita finita prima delle 20 chiamate: se ne apre un'altra
            }
            Board prima = game.board();
            boolean mossaLegaleEsiste = prima.hasMoves();

            SolverOutcome esito = game.suggerisci();
            chiamate++;
            totale += esito.millis();
            peggiore = Math.max(peggiore, esito.millis());

            assertTrue(esito.millis() <= budget.toMillis() + TOLLERANZA_MS,
                    "chiamata " + chiamate + " fuori budget: " + esito.millis() + " ms su "
                            + budget.toMillis() + "\n" + prima);

            if (mossaLegaleEsiste) {
                assertEquals(SolverStatus.OK, esito.status(),
                        "chiamata " + chiamate + ": esiste una mossa legale ma il solver ha detto "
                                + esito.status() + "\n" + prima);
                assertTrue(esito.move().isPresent(),
                        "chiamata " + chiamate + ": stato OK ma nessuna mossa\n" + prima);
                assertTrue(esito.horizonRaggiunto() >= 1,
                        "chiamata " + chiamate + ": orizzonte raggiunto " + esito.horizonRaggiunto());
                orizzonteMinimo = Math.min(orizzonteMinimo, esito.horizonRaggiunto());
                orizzonteMassimo = Math.max(orizzonteMassimo, esito.horizonRaggiunto());
                assertTrue(game.muovi(esito.move().get()),
                        "chiamata " + chiamate + ": la mossa suggerita non e' applicabile\n" + prima);
            } else {
                assertEquals(SolverStatus.NESSUNA_SOLUZIONE, esito.status(),
                        "chiamata " + chiamate + ": board bloccata, atteso NESSUNA_SOLUZIONE\n" + prima);
            }
        }

        assertEquals(20, chiamate);
        assertTrue(orizzonteMinimo >= 1, "almeno un orizzonte per chiamata");
        System.out.println("[venti_suggerimenti] budget=" + budget.toMillis()
                + "ms medio=" + (totale / chiamate) + "ms peggiore=" + peggiore
                + "ms orizzonti=" + orizzonteMinimo + ".." + orizzonteMassimo);
    }

    /**
     * Chi avvia il gioco senza binario, legge "DLV non trovato", esegue
     * scripts/fetch-dlv.sh e ripreme S deve trovarlo: la posizione va ririsolta
     * finche' risulta assente, non congelata nel costruttore.
     */
    @Test
    void il_binario_assente_viene_ririsolto_alla_chiamata_successiva(@TempDir Path dir)
            throws Exception {
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        // il file deve esistere davvero: e' cio' che simula lo script di
        // download appena eseguito. Il contenuto non e' quello autentico, quindi
        // l'esito sara' CHECKSUM_ERRATO, che e' comunque "trovato".
        Path scaricato = dir.resolve("dlv2");
        Files.writeString(scaricato, "comparso dopo il primo tentativo");
        assertTrue(scaricato.toFile().setExecutable(true));
        AtomicInteger risoluzioni = new AtomicInteger();
        Supplier<Optional<Path>> localizzatore =
                () -> risoluzioni.incrementAndGet() == 1 ? Optional.empty() : Optional.of(scaricato);

        AspSolver s = new AspSolver(2, Duration.ofSeconds(3), localizzatore);

        assertEquals(SolverStatus.BINARIO_ASSENTE, s.bestMove(b).status());
        assertNotEquals(SolverStatus.BINARIO_ASSENTE, s.bestMove(b).status(),
                "il binario comparso nel frattempo deve essere ritrovato senza riavviare il gioco");
        assertEquals(2, risoluzioni.get());
    }

    /**
     * L'attacco dimostrato in review: copiare il binario autentico (checksum
     * ok), poi sostituirne il contenuto con uno script qualsiasi rimettendo
     * l'mtime originale. Una verifica che si fidi di (percorso, mtime) non se
     * ne accorge ed esegue il binario falso; il checksum va ricalcolato a ogni
     * bestMove.
     */
    @Test
    void un_contenuto_sostituito_con_lo_stesso_mtime_produce_checksum_errato(@TempDir Path dir)
            throws Exception {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Path autentico = DlvBinary.locate().orElseThrow();
        Path copia = dir.resolve("dlv2");
        Files.copy(autentico, copia);
        copia.toFile().setExecutable(true);
        FileTime mtimeOriginale = Files.getLastModifiedTime(copia);

        Board b = Board.of(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        AspSolver s = new AspSolver(1, Duration.ofSeconds(5), Optional.of(copia));
        assertEquals(SolverStatus.OK, s.bestMove(b).status(),
                "la copia autentica deve superare il checksum ed essere eseguita");

        Files.writeString(copia, "#!/bin/sh\necho attacco\n", StandardCharsets.UTF_8);
        Files.setLastModifiedTime(copia, mtimeOriginale);

        SolverOutcome esito = s.bestMove(b);
        assertEquals(SolverStatus.CHECKSUM_ERRATO, esito.status(),
                "contenuto sostituito con mtime preservato: non deve eseguire il binario falso");
        assertTrue(esito.move().isEmpty());
    }

    /**
     * Regressione: un errore a un livello h>1 tornava ERRORE senza mossa anche
     * quando un livello precedente aveva gia' prodotto un piano valido. Il
     * piano buono non va buttato via per un guasto piu' in profondita'.
     */
    @Test
    void un_errore_a_orizzonte_maggiore_di_uno_non_butta_via_un_piano_gia_trovato() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Path bin = DlvBinary.locate().orElseThrow();
        Board b = Board.of(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        AspSolver.Esecutore esecutore = (binario, programma, timeout) -> programma.contains("horizon(1)")
                ? new DlvRunner.DlvResult(SolverStatus.OK, "move(0,l)")
                : new DlvRunner.DlvResult(SolverStatus.ERRORE, "");

        AspSolver s = new AspSolver(3, Duration.ofSeconds(5), () -> Optional.of(bin), esecutore);
        SolverOutcome o = s.bestMove(b);

        assertEquals(SolverStatus.OK, o.status(),
                "un errore a orizzonte 2 non deve buttare via il piano trovato a orizzonte 1");
        assertEquals(Optional.of(Direction.LEFT), o.move());
        assertEquals(1, o.horizonRaggiunto());
    }

    @Test
    void un_binario_gia_trovato_non_viene_ririsolto_a_ogni_mossa(@TempDir Path dir) throws Exception {
        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        // deve esistere davvero: un percorso fantasma verrebbe giustamente
        // ririsolto, ed e' proprio cio' che verifica il test successivo
        Path presente = dir.resolve("dlv2");
        Files.writeString(presente, "non e' il binario vero, ma c'e'");
        assertTrue(presente.toFile().setExecutable(true));

        AtomicInteger risoluzioni = new AtomicInteger();
        Supplier<Optional<Path>> localizzatore = () -> {
            risoluzioni.incrementAndGet();
            return Optional.of(presente);
        };

        AspSolver s = new AspSolver(2, Duration.ofSeconds(3), localizzatore);
        s.bestMove(b);
        s.bestMove(b);
        s.bestMove(b);

        assertEquals(1, risoluzioni.get(),
                "un binario gia' localizzato non va ricercato a ogni suggerimento");
    }

    /**
     * Il caso peggiore: il binario sparisce dopo essere stato usato. Riportare
     * CHECKSUM_ERRATO accuserebbe l'utente di avere un binario manomesso e non
     * gli direbbe mai di eseguire fetch-dlv.sh. File assente e file manomesso
     * sono due guasti diversi e vanno detti in modo diverso.
     */
    @Test
    void un_binario_cancellato_dopo_l_uso_non_diventa_un_falso_checksum_errato(@TempDir Path dir)
            throws Exception {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Path copia = dir.resolve("dlv2");
        Files.copy(DlvBinary.locate().orElseThrow(), copia);
        assertTrue(copia.toFile().setExecutable(true));

        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        AspSolver s = new AspSolver(1, Duration.ofSeconds(5), Optional.of(copia));
        assertEquals(SolverStatus.OK, s.bestMove(b).status());

        Files.delete(copia);
        assertEquals(SolverStatus.BINARIO_ASSENTE, s.bestMove(b).status());
    }

    @Test
    void un_binario_ripristinato_torna_a_funzionare_senza_riavviare(@TempDir Path dir)
            throws Exception {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Path autentico = DlvBinary.locate().orElseThrow();
        Path copia = dir.resolve("dlv2");
        Files.copy(autentico, copia);
        assertTrue(copia.toFile().setExecutable(true));

        Board b = Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0);
        AspSolver s = new AspSolver(1, Duration.ofSeconds(5), Optional.of(copia));
        assertEquals(SolverStatus.OK, s.bestMove(b).status());

        Files.delete(copia);
        assertEquals(SolverStatus.BINARIO_ASSENTE, s.bestMove(b).status());

        Files.copy(autentico, copia);
        assertTrue(copia.toFile().setExecutable(true));
        assertEquals(SolverStatus.OK, s.bestMove(b).status(),
                "ripristinato il binario, il solver deve tornare utilizzabile");
    }

    /**
     * TIMEOUT era l'unico stato mai asserito da un test, pur essendo quello che
     * l'utente incontra piu' spesso quando la macchina e' carica.
     */
    @Test
    void quando_nemmeno_il_primo_livello_rientra_nel_budget_lo_stato_e_timeout() {
        assumeTrue(dlvDisponibile(), "DLV2 non installato");
        Path bin = DlvBinary.locate().orElseThrow();
        AspSolver s = new AspSolver(4, Duration.ofSeconds(3), () -> Optional.of(bin),
                (b, programma, timeout) -> new DlvRunner.DlvResult(SolverStatus.TIMEOUT, ""));

        SolverOutcome o = s.bestMove(Board.of(1,1,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0));

        assertEquals(SolverStatus.TIMEOUT, o.status());
        assertTrue(o.move().isEmpty());
        assertEquals(0, o.horizonRaggiunto());
    }
}
