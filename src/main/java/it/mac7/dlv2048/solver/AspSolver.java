package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Solver che delega la scelta della mossa a DLV2 su un problema di pianificazione,
 * con approfondimento iterativo.
 *
 * <p>Un orizzonte fisso non e' governabile: il costo di DLV raddoppia circa a ogni
 * livello e dipende da quanto e' piena la board, quindi a orizzonte 6 la stessa
 * partita alternava risposte in mezzo secondo e timeout secchi (misurato: 9
 * fallimenti su 20 pressioni di S). L'approfondimento iterativo rovescia il
 * contratto: il parametro governato e' il BUDGET TOTALE, l'orizzonte e' quello che
 * ci sta dentro. Si parte da 1 e si sale finche' il tempo residuo basta,
 * conservando sempre l'ultimo piano riuscito.
 *
 * <p>Partire da 1 chiude anche un difetto latente: il programma ASP pretende una
 * mossa legale a OGNI passo, quindi vicino al game over un piano lungo potrebbe non
 * esistere pur esistendo una mossa. Con l'orizzonte fisso quel caso diventava
 * "nessuna mossa possibile"; qui il livello 1 ha gia' dato la risposta.
 */
public final class AspSolver implements Solver {

    private static final String RISORSA = "/asp/plan.dlv2";

    /** Oltre questa profondita' il guadagno non ripaga il costo: serve solo da tetto. */
    public static final int ORIZZONTE_MAX_DEFAULT = 8;

    private static final Duration BUDGET_DEFAULT = Duration.ofSeconds(3);

    /** Esecuzione di un livello, iniettabile nei test al posto del processo DLV2 vero. */
    @FunctionalInterface
    interface Esecutore {
        DlvRunner.DlvResult run(Path binary, String programma, Duration timeout);
    }

    private final int horizonMax;
    private final Duration budget;
    private final String programma;

    /** Come ritrovare il binario. Interrogato di nuovo finche' l'esito e' vuoto. */
    private final Supplier<Optional<Path>> localizzatore;

    private final Esecutore esecutore;

    /** Ultimo binario localizzato. Volatile: il solver gira sul worker della GUI. */
    private volatile Optional<Path> binary = Optional.empty();

    public AspSolver() {
        this(ORIZZONTE_MAX_DEFAULT, BUDGET_DEFAULT);
    }

    public AspSolver(int horizonMax, Duration budget) {
        this(horizonMax, budget, DlvBinary::locate);
    }

    /**
     * Binario imposto dal chiamante: nessuna ricerca, nemmeno se assente.
     * Serve ai test e a chi vuole puntare a una copia specifica.
     */
    public AspSolver(int horizonMax, Duration budget, Optional<Path> binary) {
        this(horizonMax, budget, () -> binary);
    }

    AspSolver(int horizonMax, Duration budget, Supplier<Optional<Path>> localizzatore) {
        this(horizonMax, budget, localizzatore, DlvRunner::run);
    }

    AspSolver(int horizonMax, Duration budget, Supplier<Optional<Path>> localizzatore, Esecutore esecutore) {
        if (horizonMax < 1) {
            throw new IllegalArgumentException(
                    "orizzonte massimo deve essere >= 1, ricevuto " + horizonMax);
        }
        this.horizonMax = horizonMax;
        this.budget = budget;
        this.localizzatore = localizzatore;
        this.esecutore = esecutore;
        this.programma = caricaProgramma();
    }

    private static String caricaProgramma() {
        try (InputStream in = AspSolver.class.getResourceAsStream(RISORSA)) {
            if (in == null) throw new IllegalStateException("risorsa mancante: " + RISORSA);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("impossibile leggere " + RISORSA, e);
        }
    }

    @Override
    public SolverOutcome bestMove(Board board) {
        long t0 = System.nanoTime();

        Optional<Path> trovato = binarioCorrente();
        if (trovato.isEmpty()) {
            return SolverOutcome.fallito(SolverStatus.BINARIO_ASSENTE, elapsed(t0));
        }
        Path bin = trovato.get();
        // Ricalcolato a ogni chiamata, non memoizzato: una cache su (percorso,
        // mtime) e' aggirabile sostituendo il contenuto del binario e
        // rimettendo l'mtime originale. Il costo (pochi ms su un binario di
        // 3 MB) e' trascurabile sul budget di ricerca.
        if (!DlvBinary.checksumValido(bin)) {
            return SolverOutcome.fallito(SolverStatus.CHECKSUM_ERRATO, elapsed(t0));
        }

        long budgetMs = budget.toMillis();
        Optional<Direction> migliore = Optional.empty();
        int horizonRaggiunto = 0;
        long costoLivelloPrecedente = 0;

        for (int h = 1; h <= horizonMax; h++) {
            long rimasto = budgetMs - elapsed(t0);
            if (rimasto <= 0) break;
            // Un tentativo che va in timeout non spreca il budget: DlvRunner e'
            // comunque limitato al residuo, quindi conviene provare finche' resta
            // almeno il tempo dell'ultimo livello pagato, non il suo doppio.
            if (h > 1 && rimasto < costoLivelloPrecedente) break;

            long inizioLivello = System.nanoTime();
            DlvRunner.DlvResult res = esecutore.run(
                    bin,
                    programma + "\n" + AspEncoder.facts(board, h),
                    Duration.ofMillis(rimasto)); // solo il residuo, non il budget intero
            costoLivelloPrecedente = elapsed(inizioLivello);

            if (res.status() == SolverStatus.TIMEOUT) {
                break; // il livello non ce l'ha fatta: vale l'ultimo piano riuscito
            }
            if (res.status() != SolverStatus.OK) {
                // errore vero (uscita anomala di DLV, I/O). Se un livello precedente ha
                // gia' prodotto un piano, quel piano resta valido: buttarlo per un
                // guasto piu' in profondita' sarebbe peggio che restituirlo per buono.
                if (migliore.isPresent()) {
                    return new SolverOutcome(migliore, SolverStatus.OK, elapsed(t0), horizonRaggiunto);
                }
                return new SolverOutcome(Optional.empty(), res.status(), elapsed(t0), horizonRaggiunto);
            }

            Optional<Direction> mossa = AnswerSetParser.firstMove(res.stdout());
            if (mossa.isEmpty()) {
                // Se esiste un piano di lunghezza h+1, il suo prefisso di lunghezza h
                // e' a sua volta un piano valido: l'assenza a h implica l'assenza a h+1.
                // Approfondire ancora e' tempo buttato.
                if (horizonRaggiunto == 0) {
                    // gia' a orizzonte 1: non esiste alcuna mossa legale
                    return SolverOutcome.fallito(SolverStatus.NESSUNA_SOLUZIONE, elapsed(t0));
                }
                break;
            }

            migliore = mossa;
            horizonRaggiunto = h;
        }

        if (horizonRaggiunto == 0) {
            // nemmeno il primo livello e' rientrato nel budget
            return SolverOutcome.fallito(SolverStatus.TIMEOUT, elapsed(t0));
        }
        return new SolverOutcome(migliore, SolverStatus.OK, elapsed(t0), horizonRaggiunto);
    }

    /**
     * Il binario va ricercato di nuovo finche' non lo si trova: chi legge
     * "DLV non trovato", lancia scripts/fetch-dlv.sh e ripreme S deve vedere il
     * risultato senza riavviare il gioco. Una volta trovato la ricerca si ferma,
     * perche' scandire il PATH a ogni suggerimento non aggiunge nulla.
     */
    private Optional<Path> binarioCorrente() {
        Optional<Path> corrente = binary;
        if (corrente.isPresent()) return corrente;
        Optional<Path> risolto = localizzatore.get();
        binary = risolto;
        return risolto;
    }

    private static long elapsed(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}
