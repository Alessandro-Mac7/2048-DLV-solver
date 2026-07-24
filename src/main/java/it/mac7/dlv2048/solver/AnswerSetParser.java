package it.mac7.dlv2048.solver;

import it.mac7.dlv2048.core.Direction;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Estrae la mossa scelta dall'answer set stampato da DLV2. */
public final class AnswerSetParser {

    // (?<![a-z]) impedisce di matchare "move" dentro "nomove"
    private static final Pattern MOVE =
            Pattern.compile("(?<![a-z])move\\((\\d+),([udlr])\\)");

    private AnswerSetParser() {}

    public static Optional<Direction> firstMove(String dlvOutput) {
        if (dlvOutput == null || dlvOutput.isBlank()) return Optional.empty();
        Matcher m = MOVE.matcher(dlvOutput);
        while (m.find()) {
            if ("0".equals(m.group(1))) {
                return Optional.of(Direction.fromAspCode(m.group(2).charAt(0)));
            }
        }
        return Optional.empty();
    }
}
