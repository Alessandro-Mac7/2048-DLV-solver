package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Board;
import it.mac7.dlv2048.core.Direction;
import it.mac7.dlv2048.core.MoveResult;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica che la meccanica ASP e quella Java producano la STESSA board.
 * E' l'unico test che impedisce alle due implementazioni di divergere.
 */
class MeccanicaCoerenteTest {

    private static final Pattern AT =
            Pattern.compile("at\\(1,(\\d),(\\d),(\\d+)\\)");

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

    /** Forza ASP a eseguire una direzione specifica e restituisce lo stato a T=1. */
    private static Optional<Board> applicaConAsp(Path bin, Board b, Direction d) throws Exception {
        String programma = new String(MeccanicaCoerenteTest.class
                .getResourceAsStream("/asp/plan.dlv2").readAllBytes());

        // orizzonte 1, direzione imposta, e nessuna ottimizzazione di mezzo
        String istanza = AspEncoder.facts(b, 1)
                + ":- not move(0," + d.aspCode() + ").\n";

        Path tmp = Files.createTempFile("coerenza-", ".asp");
        Files.writeString(tmp, programma + "\n" + istanza);

        Process p = new ProcessBuilder(bin.toString(), "--silent",
                "--printonlyoptimum", "--filter=at/4", tmp.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        p.waitFor();
        Files.deleteIfExists(tmp);

        if (!out.contains("at(1,")) return Optional.empty();

        int[] celle = new int[16];
        Matcher m = AT.matcher(out);
        while (m.find()) {
            int r = Integer.parseInt(m.group(1));
            int c = Integer.parseInt(m.group(2));
            celle[r * 4 + c] = Integer.parseInt(m.group(3));
        }
        return Optional.of(Board.of(celle));
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
