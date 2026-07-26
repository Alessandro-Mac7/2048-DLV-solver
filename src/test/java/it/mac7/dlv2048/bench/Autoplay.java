package it.mac7.dlv2048.bench;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.MoveResult;
import it.mac7.dlv2048.solver.AnswerSetParser;
import it.mac7.dlv2048.solver.AspEncoder;
import it.mac7.dlv2048.solver.DlvBinary;
import it.mac7.dlv2048.solver.DlvRunner;
import it.mac7.dlv2048.solver.SolverStatus;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Banco di prova: fa giocare partite complete pilotate da DLV2 e riporta le
 * statistiche aggregate. Serve a rispondere all'unica domanda che conta su un
 * cambiamento di strategia — il solver gioca meglio o no.
 *
 * <p>NON e' un test JUnit e non deve diventarlo: una singola partita costa
 * minuti, e la suite normale deve restare veloce. Si esegue a mano:
 *
 * <pre>
 * ./mvnw -q test-compile
 * DLV2_HOME=... java -cp target/classes:target/test-classes \
 *     it.mac7.dlv2048.bench.Autoplay --partite=24 --orizzonte=3
 * </pre>
 *
 * <p>Per confrontare due programmi ASP sullo stesso seme, si passa
 * {@code --asp=<file>}: il riferimento si estrae dalla storia con
 * {@code git show <rev>:src/main/resources/asp/plan.dlv2 > /tmp/rif.dlv2}.
 *
 * <p>L'orizzonte e' FISSO, non a budget: un budget temporale farebbe dipendere
 * la profondita' raggiunta dal carico della macchina, e due esecuzioni della
 * stessa configurazione non sarebbero piu' confrontabili.
 */
public final class Autoplay {

    /** Oltre questa lunghezza la partita e' comunque decisa: taglia le code patologiche. */
    private static final int MOSSE_MAX = 5000;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private Autoplay() {}

    /** Esito di una partita. */
    record Partita(int mosse, int punteggio, int maxEsponente,
                   int senzaPiano, int errori, long msDlv, int chiamate) {}

    public static void main(String[] args) throws Exception {
        Config cfg = Config.parse(args);
        Path bin = DlvBinary.locate().orElseThrow(
                () -> new IllegalStateException("DLV2 non trovato: imposta DLV2_HOME"));

        String programma = cfg.programma();
        System.out.printf("asp=%s partite=%d orizzonte=%d seme=%d thread=%d%n",
                cfg.aspDescrizione(), cfg.partite, cfg.orizzonte, cfg.seme, cfg.thread);

        AtomicInteger finite = new AtomicInteger();
        List<Future<Partita>> futuri = new ArrayList<>();
        long t0 = System.nanoTime();

        try (ExecutorService pool = Executors.newFixedThreadPool(cfg.thread)) {
            for (int i = 0; i < cfg.partite; i++) {
                long seme = cfg.seme + i;
                futuri.add(pool.submit(() -> {
                    Partita p = gioca(bin, programma, cfg.orizzonte, seme);
                    System.out.printf("  partita seme=%d  max=%d punti=%d mosse=%d "
                            + "senzaPiano=%d errori=%d  (%d/%d)%n",
                            seme, 1 << p.maxEsponente(), p.punteggio(), p.mosse(),
                            p.senzaPiano(), p.errori(), finite.incrementAndGet(), cfg.partite);
                    return p;
                }));
            }

            List<Partita> esiti = new ArrayList<>();
            for (Future<Partita> f : futuri) esiti.add(f.get());
            stampaRiepilogo(esiti, (System.nanoTime() - t0) / 1_000_000_000L);
        }
    }

    /**
     * Una partita completa. Lo stato di vittoria e' deliberatamente ignorato:
     * qui interessa quanto lontano arriva il solver, non se tocca 2048.
     */
    static Partita gioca(Path bin, String programma, int orizzonte, long seme) {
        Random rnd = new Random(seme);
        Board board = Board.empty();
        board = conTesseraCasuale(board, rnd);
        board = conTesseraCasuale(board, rnd);

        int mosse = 0, punteggio = 0, senzaPiano = 0, errori = 0, chiamate = 0;
        long msDlv = 0;

        while (board.hasMoves() && mosse < MOSSE_MAX) {
            long t = System.nanoTime();
            Scelta s = scegli(bin, programma, board, orizzonte);
            msDlv += (System.nanoTime() - t) / 1_000_000;
            chiamate += s.chiamate();
            errori += s.errori();

            Direction d = s.mossa().orElse(null);
            if (d == null || !board.move(d).moved()) {
                // il programma non ha prodotto un piano dove una mossa legale
                // esiste: si prosegue con una mossa legale qualsiasi, ma il
                // caso va contato perche' e' un difetto del modello
                senzaPiano++;
                d = primaLegale(board);
                if (d == null) break;
            }
            MoveResult r = board.move(d);
            board = r.board();
            punteggio += r.gainedScore();
            mosse++;
            board = conTesseraCasuale(board, rnd);
        }
        return new Partita(mosse, punteggio, board.maxExponent(),
                senzaPiano, errori, msDlv, chiamate);
    }

    private record Scelta(Optional<Direction> mossa, int chiamate, int errori) {}

    /**
     * Chiede il piano all'orizzonte pieno e scende se non esiste. Rispecchia il
     * comportamento di AspSolver, che conserva l'ultimo livello riuscito: vicino
     * al game over un piano lungo puo' non esistere pur esistendo una mossa.
     */
    private static Scelta scegli(Path bin, String programma, Board board, int orizzonte) {
        int chiamate = 0, errori = 0;
        for (int h = orizzonte; h >= 1; h--) {
            chiamate++;
            DlvRunner.DlvResult res = DlvRunner.run(
                    bin, programma + "\n" + AspEncoder.facts(board, h), TIMEOUT);
            if (res.status() != SolverStatus.OK) {
                errori++;
                continue;
            }
            Optional<Direction> m = AnswerSetParser.firstMove(res.stdout());
            if (m.isPresent()) return new Scelta(m, chiamate, errori);
        }
        return new Scelta(Optional.empty(), chiamate, errori);
    }

    private static Direction primaLegale(Board b) {
        for (Direction d : Direction.values()) {
            if (b.move(d).moved()) return d;
        }
        return null;
    }

    /** Identica a Game.aggiungiTesseraCasuale: 4 col 10%, altrimenti 2. */
    private static Board conTesseraCasuale(Board b, Random rnd) {
        List<int[]> vuote = b.emptyCells();
        if (vuote.isEmpty()) return b;
        int[] cella = vuote.get(rnd.nextInt(vuote.size()));
        return b.withTile(cella[0], cella[1], rnd.nextInt(10) == 0 ? 2 : 1);
    }

    private static void stampaRiepilogo(List<Partita> esiti, long secondi) {
        int n = esiti.size();
        double punti = esiti.stream().mapToInt(Partita::punteggio).average().orElse(0);
        double mosse = esiti.stream().mapToInt(Partita::mosse).average().orElse(0);
        int senzaPiano = esiti.stream().mapToInt(Partita::senzaPiano).sum();
        int errori = esiti.stream().mapToInt(Partita::errori).sum();
        long msDlv = esiti.stream().mapToLong(Partita::msDlv).sum();
        int chiamate = esiti.stream().mapToInt(Partita::chiamate).sum();

        Map<Integer, Integer> istogramma = new TreeMap<>();
        for (Partita p : esiti) istogramma.merge(1 << p.maxEsponente(), 1, Integer::sum);

        List<Integer> punteggi = esiti.stream().map(Partita::punteggio)
                .sorted(Comparator.naturalOrder()).toList();

        System.out.println();
        System.out.println("=== riepilogo su " + n + " partite ===");
        System.out.printf("punteggio  medio=%.0f  mediana=%d  min=%d  max=%d  ds=%.0f%n",
                punti, punteggi.get(n / 2), punteggi.getFirst(), punteggi.getLast(),
                scarto(punteggi, punti));
        System.out.printf("mosse      medie=%.0f%n", mosse);
        System.out.printf("tessera massima: %s%n", istogramma);
        System.out.printf("tessera >= 512 in %d/%d partite%n",
                esiti.stream().filter(p -> p.maxEsponente() >= 9).count(), n);
        System.out.printf("tessera >= 1024 in %d/%d partite%n",
                esiti.stream().filter(p -> p.maxEsponente() >= 10).count(), n);
        System.out.printf("senzaPiano=%d errori=%d chiamate=%d  ms/chiamata=%.0f%n",
                senzaPiano, errori, chiamate, chiamate == 0 ? 0 : (double) msDlv / chiamate);
        System.out.printf("durata reale: %d s%n", secondi);
    }

    private static double scarto(List<Integer> valori, double media) {
        double q = valori.stream().mapToDouble(v -> (v - media) * (v - media)).sum();
        return Math.sqrt(q / valori.size());
    }

    /** Configurazione da riga di comando. */
    private record Config(Path asp, int partite, int orizzonte, long seme, int thread) {

        static Config parse(String[] args) {
            Path asp = null;
            int partite = 20, orizzonte = 3, thread = 6;
            long seme = 20260726L;
            for (String a : args) {
                String[] kv = a.split("=", 2);
                switch (kv[0]) {
                    case "--asp" -> asp = Path.of(kv[1]);
                    case "--partite" -> partite = Integer.parseInt(kv[1]);
                    case "--orizzonte" -> orizzonte = Integer.parseInt(kv[1]);
                    case "--seme" -> seme = Long.parseLong(kv[1]);
                    case "--thread" -> thread = Integer.parseInt(kv[1]);
                    default -> throw new IllegalArgumentException("opzione ignota: " + kv[0]);
                }
            }
            return new Config(asp, partite, orizzonte, seme, thread);
        }

        String programma() throws Exception {
            if (asp != null) return Files.readString(asp, StandardCharsets.UTF_8);
            try (InputStream in = Autoplay.class.getResourceAsStream("/asp/plan.dlv2")) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        String aspDescrizione() {
            return asp == null ? "risorsa /asp/plan.dlv2" : asp.toString();
        }
    }
}
