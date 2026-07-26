package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estrae la mossa scelta dall'answer set stampato da DLV2. */
public final class AnswerSetParser {

    // (?<![A-Za-z0-9_]) è un confine di parola reale: impedisce di matchare
    // "move" dentro "nomove", "auto_move", "x1move", "PREmove", ecc.
    private static final Pattern MOVE =
            Pattern.compile("(?<![A-Za-z0-9_])move\\((\\d+),([udlr])\\)");

    private AnswerSetParser() {}

    public static Optional<Direction> firstMove(String dlvOutput) {
        if (dlvOutput == null || dlvOutput.isBlank()) return Optional.empty();
        Matcher m = MOVE.matcher(dlvOutput);
        // DLV2, senza --printonlyoptimum, stampa un modello ogni volta che
        // migliora il costo: l'ultimo prima di OPTIMUM è quello ottimo.
        // Bisogna quindi tenere l'ultima occorrenza del passo 0, non la prima.
        Optional<Direction> last = Optional.empty();
        while (m.find()) {
            if ("0".equals(m.group(1))) {
                last = Optional.of(Direction.fromAspCode(m.group(2).charAt(0)));
            }
        }
        return last;
    }
}
