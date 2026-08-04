package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Il programma vieta "giu'" quando esiste un'alternativa legale, per dimezzare
 * lo spazio di ricerca. La restrizione ha una proprieta' di sicurezza che non e'
 * negoziabile: dove una mossa legale esiste, il solver deve rispondere.
 *
 * <p>Non e' zelo. L'albero avversariale ci era gia' cascato: {@code step/1} era
 * diventato un predicato derivato dentro una componente con negazione ricorsiva,
 * il vincolo "a ogni passo serve una mossa" aveva smesso di mordere e DLV2
 * rispondeva "nessuna mossa" su board con quattro mosse legali, 32 volte su 321.
 * Un errore di quella forma non si vede in un test di punteggio: si vede solo
 * chiedendo a molte board diverse se una risposta arriva.
 */
class VentaglioRistrettoTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static String programma() throws Exception {
        try (InputStream in = VentaglioRistrettoTest.class.getResourceAsStream("/asp/plan.dlv2")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Il test centrale: molte board diverse, tutte con almeno una mossa legale,
     * e a orizzonte 1 deve sempre arrivare una mossa applicabile. L'orizzonte 1
     * e' quello che conta perche' e' l'ultima spiaggia dell'approfondimento
     * iterativo: se resta coerente li', il solver non dira' mai "nessuna
     * soluzione" su una board viva.
     */
    @Test
    void su_board_con_mosse_legali_arriva_sempre_una_mossa() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        String prog = programma();
        Random rnd = new Random(20260726L);
        int provate = 0;

        for (int iter = 0; iter < 120; iter++) {
            Board b = boardCasuale(rnd);
            if (!b.hasMoves()) continue;

            Optional<Direction> m = mossa(bin.get(), prog, b, 1);
            assertTrue(m.isPresent(),
                    "board con mosse legali ma nessun piano a orizzonte 1:\n" + b);
            assertTrue(b.move(m.get()).moved(),
                    "mossa proposta non applicabile: " + m.get() + "\n" + b);
            provate++;
        }
        assertTrue(provate > 100, "troppe poche board vive nel campione: " + provate);
    }

    /**
     * Il caso che la restrizione deve lasciar passare: tessere impacchettate in
     * alto a sinistra senza coppie fondibili. Ne' "sinistra" ne' "su" scorrono,
     * quindi la sola alternativa e' proprio la direzione vietata (o "destra"), e
     * un divieto incondizionato renderebbe il programma incoerente qui.
     */
    @Test
    void quando_sinistra_e_su_sono_illegali_il_divieto_si_ritira() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        String prog = programma();
        Board[] impacchettate = {
            Board.of(1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0),
            Board.of(1,2,0,0, 3,4,0,0, 0,0,0,0, 0,0,0,0),
            Board.of(1,2,3,0, 4,5,6,0, 7,8,9,0, 0,0,0,0),
        };

        for (Board b : impacchettate) {
            assertTrue(b.hasMoves());
            assertFalse(b.move(Direction.LEFT).moved(), "board mal costruita: sinistra scorre\n" + b);
            assertFalse(b.move(Direction.UP).moved(), "board mal costruita: su scorre\n" + b);

            Optional<Direction> m = mossa(bin.get(), prog, b, 1);
            assertTrue(m.isPresent(), "nessuna mossa dove restano solo giu' e destra:\n" + b);
            assertTrue(b.move(m.get()).moved(), "mossa non applicabile: " + m.get() + "\n" + b);
        }
    }

    /**
     * E la restrizione deve anche mordere davvero, altrimenti non fa risparmiare
     * nulla: dove "sinistra" oppure "su" sono legali, "giu'" non va mai scelta.
     */
    @Test
    void dove_esiste_un_alternativa_legale_giu_non_viene_mai_scelta() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        String prog = programma();
        Random rnd = new Random(20260727L);
        int verificate = 0;

        for (int iter = 0; iter < 80; iter++) {
            Board b = boardCasuale(rnd);
            boolean alternativa = b.move(Direction.LEFT).moved() || b.move(Direction.UP).moved();
            if (!alternativa) continue;

            Optional<Direction> m = mossa(bin.get(), prog, b, 1);
            assertTrue(m.isPresent(), "nessun piano su board viva:\n" + b);
            assertNotEquals(Direction.DOWN, m.get(),
                    "giu' scelta pur essendoci sinistra o su legali:\n" + b);
            verificate++;
        }
        assertTrue(verificate > 60, "campione troppo piccolo: " + verificate);
    }

    /**
     * A orizzonte maggiore di 1 la restrizione potrebbe in teoria portare il
     * piano in un vicolo cieco a meta' strada. Il solver completo scende di
     * livello e deve comunque rispondere: e' il contratto che l'utente vede.
     */
    @Test
    void il_solver_completo_risponde_sempre_su_board_viva() {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        Random rnd = new Random(20260728L);
        AspSolver s = new AspSolver(4, Duration.ofSeconds(3));
        int provate = 0;

        for (int iter = 0; iter < 25; iter++) {
            Board b = boardCasuale(rnd);
            if (!b.hasMoves()) continue;

            SolverOutcome o = s.bestMove(b);
            assertEquals(SolverStatus.OK, o.status(), "board viva ma stato " + o.status() + "\n" + b);
            assertTrue(o.move().isPresent(), "stato OK senza mossa\n" + b);
            assertTrue(b.move(o.move().get()).moved(), "mossa non applicabile\n" + b);
            provate++;
        }
        assertTrue(provate > 20, "campione troppo piccolo: " + provate);
    }

    private static Optional<Direction> mossa(Path bin, String prog, Board b, int orizzonte) {
        DlvRunner.DlvResult res =
                DlvRunner.run(bin, prog + "\n" + AspEncoder.facts(b, orizzonte), TIMEOUT);
        assertEquals(SolverStatus.OK, res.status(), "DLV2 non ha terminato normalmente\n" + b);
        return AnswerSetParser.firstMove(res.stdout());
    }

    /** Board dense a esponenti bassi: piu' merge possibili, quindi caso piu' severo. */
    private static Board boardCasuale(Random rnd) {
        int[] celle = new int[16];
        int quante = 6 + rnd.nextInt(9);
        for (int i = 0; i < quante; i++) {
            celle[rnd.nextInt(16)] = 1 + rnd.nextInt(5);
        }
        return Board.of(celle);
    }
}
