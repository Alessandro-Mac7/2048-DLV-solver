package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.MoveResult;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica che la meccanica ASP e quella Java producano la STESSA board.
 * E' l'unico test che impedisce alle due implementazioni di divergere.
 */
class MeccanicaCoerenteTest {

    @Test
    void asp_e_java_concordano_su_board_casuali() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        // seme fisso: un fallimento deve essere riproducibile
        Random rnd = new Random(20260725L);
        int confronti = 0;

        for (int iter = 0; iter < 40; iter++) {
            Board b = boardCasuale(rnd);
            for (Direction d : Direction.values()) {
                MoveResult atteso = b.move(d);
                if (!atteso.moved()) continue;   // ASP rifiuta le mosse illegali

                Optional<Board> ottenuto = applicaConAsp(bin.get(), b, d);
                assertTrue(ottenuto.isPresent(),
                        "ASP non ha prodotto uno stato per " + d + " su\n" + b);
                assertEquals(atteso.board(), ottenuto.get(),
                        "divergenza su " + d + "\npartenza:\n" + b
                        + "atteso (Java):\n" + atteso.board()
                        + "ottenuto (ASP):\n" + ottenuto.get());
                confronti++;
            }
        }
        assertTrue(confronti > 50, "troppi pochi confronti utili: " + confronti);
    }

    /**
     * Il solver in esercizio pianifica su 4-6 passi, non su uno. La regola che
     * produce lo stato successivo e i ranghi che la alimentano si applicano a
     * ogni T: una divergenza che compaia solo da T=2 in poi non sarebbe vista
     * dal confronto a un passo.
     */
    @Test
    void asp_e_java_concordano_anche_su_due_passi() throws Exception {
        Optional<Path> bin = DlvBinary.locate();
        assumeTrue(bin.isPresent(), "DLV2 non installato");

        Random rnd = new Random(20260726L);
        int confronti = 0;

        for (int iter = 0; iter < 15; iter++) {
            Board b = boardCasuale(rnd);
            for (Direction prima : Direction.values()) {
                MoveResult dopoPrima = b.move(prima);
                if (!dopoPrima.moved()) continue;

                for (Direction seconda : Direction.values()) {
                    MoveResult dopoSeconda = dopoPrima.board().move(seconda);
                    if (!dopoSeconda.moved()) continue;

                    Optional<Board> ottenuto = applicaConAsp(bin.get(), b, prima, seconda);
                    assertTrue(ottenuto.isPresent(),
                            "ASP non ha prodotto uno stato per " + prima + "," + seconda
                            + " su\n" + b);
                    assertEquals(dopoSeconda.board(), ottenuto.get(),
                            "divergenza a due passi su " + prima + "," + seconda
                            + "\npartenza:\n" + b
                            + "atteso (Java):\n" + dopoSeconda.board()
                            + "ottenuto (ASP):\n" + ottenuto.get());
                    confronti++;
                }
            }
        }
        assertTrue(confronti > 30, "troppi pochi confronti a due passi: " + confronti);
    }

    /**
     * Forza ASP a eseguire esattamente la sequenza data e restituisce lo stato
     * finale. Imporre ogni mossa toglie di mezzo l'ottimizzazione: qui si
     * confronta la meccanica, non la scelta.
     */
    private static Optional<Board> applicaConAsp(Path bin, Board b, Direction... sequenza)
            throws Exception {
        String programma = new String(MeccanicaCoerenteTest.class
                .getResourceAsStream("/asp/plan.dlv2").readAllBytes());

        int passi = sequenza.length;
        StringBuilder istanza = new StringBuilder(AspEncoder.facts(b, passi));
        for (int t = 0; t < passi; t++) {
            istanza.append(":- not move(").append(t).append(',')
                   .append(sequenza[t].aspCode()).append(").\n");
        }
        Pattern AT = Pattern.compile("at\\(" + passi + ",(\\d),(\\d),(\\d+)\\)");

        Path tmpIn = Files.createTempFile("coerenza-", ".asp");
        Path tmpOut = Files.createTempFile("coerenza-out-", ".txt");
        Process p = null;
        try {
            Files.writeString(tmpIn, programma + "\n" + istanza);

            p = new ProcessBuilder(bin.toString(), "--silent",
                    "--printonlyoptimum", "--filter=at/4", tmpIn.toString())
                    .redirectErrorStream(true)
                    .redirectOutput(tmpOut.toFile())
                    .start();

            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return Optional.empty();
            }
            String out = Files.readString(tmpOut);

            if (!out.contains("at(" + passi + ",")) return Optional.empty();

            int[] celle = new int[16];
            Matcher m = AT.matcher(out);
            while (m.find()) {
                int r = Integer.parseInt(m.group(1));
                int c = Integer.parseInt(m.group(2));
                celle[r * 4 + c] = Integer.parseInt(m.group(3));
            }
            return Optional.of(Board.of(celle));
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
            Files.deleteIfExists(tmpIn);
            Files.deleteIfExists(tmpOut);
        }
    }

    private static Board boardCasuale(Random rnd) {
        int[] celle = new int[16];
        int quante = 3 + rnd.nextInt(11);
        for (int i = 0; i < quante; i++) {
            int pos = rnd.nextInt(16);
            // esponenti bassi: piu' probabilita' di merge, quindi test piu' severo
            celle[pos] = 1 + rnd.nextInt(4);
        }
        return Board.of(celle);
    }
}
